package org.astrogrid.samp.bridge;

import java.awt.Image;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.client.TrackedClientSet;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * Takes care of client connections for the SAMP Bridge.
 * An instance of this class is associated with a given 'local' hub 
 * participating in the bridge, and makes the following connections:
 * <ol>
 * <li>On the local hub, one connection which is there to monitor 
 *     client changes</li>
 * <li>On each remote hub participating in the bridge, one 'proxy' connection 
 *     for every client on this manager's local hub.</li>
 * </ol>
 * Callbacks from the hub to the proxy clients can be tunnelled by this
 * proxy manager to their true destination on the local hub.
 * Note that each proxy manager needs the cooperation of all the other
 * proxy managers (the ones associated with the other bridged hubs) to
 * make this work, so each instance of this class must be made aware of
 * the other ProxyMangers before use (see {@link #init}).
 * 
 * @author   Mark Taylor
 * @since    15 Jul 2009
 */
class ProxyManager {

    private final ClientProfile localProfile_;
    private final UtilServer server_;
    private final HubConnector pmConnector_;
    private final Map connectionMap_;  // local client ID -> HubConnection[]
    private final Map tagMap_;
    private final IconAdjuster iconAdjuster_;
    private ProxyManager[] remoteManagers_;
    private UrlExporter exporter_;
    private boolean useProxyHub_;
    private int nRemote_;

    private static final Logger logger_ =
        Logger.getLogger( ProxyManager.class.getName() );

    /**
     * Constructor.
     *
     * @param  localProfile  profile for connection to this manager's local hub
     * @param  server        server instance
     */
    public ProxyManager( ClientProfile localProfile, UtilServer server ) {
        localProfile_ = localProfile;
        server_ = server;

        // Set up the local hub connection to monitor client list changes.
        pmConnector_ =
            new HubConnector( localProfile, new ProxyManagerClientSet() ) {
                protected void connectionChanged( boolean isConnected ) {
                    super.connectionChanged( isConnected );
                    managerConnectionChanged( isConnected );
                }
            };
        Metadata meta = new Metadata();
        meta.setName( "bridge" );
        meta.setDescriptionText( "Bridge between hubs" );
        try {
            meta.setIconUrl( server_
                            .exportResource( "/org/astrogrid/samp/images/"
                                           + "bridge.png" )
                            .toString() );
        }
        catch ( IOException e ) {
            logger_.warning( "Couldn't set icon" );
        }
        meta.put( "author.name", "Mark Taylor" );
        meta.put( "author.email", "m.b.taylor@bristol.ac.uk" );
        pmConnector_.declareMetadata( meta );
        Subscriptions subs = pmConnector_.computeSubscriptions();
        pmConnector_.declareSubscriptions( subs );

        // Set up other required data structures.
        connectionMap_ = Collections.synchronizedMap( new HashMap() );
        tagMap_ = Collections.synchronizedMap( new HashMap() );
        iconAdjuster_ = new ProxyIconAdjuster();
        server_.getServer().addHandler( iconAdjuster_ );
    }

    /**
     * Returns the profile for this manager's local hub.
     *
     * @return  profile
     */
    public ClientProfile getProfile() {
        return localProfile_;
    }

    /**
     * Returns the hub connector used by this manager for client monitoring
     * on the local hub.
     *
     * @return   hub connector 
     */
    public HubConnector getManagerConnector() {
        return pmConnector_;
    }

    /**
     * Sets an object which is used to export SAMP data contents for use
     * in remote contexts.
     *
     * @param   exporter  new exporter; may be null
     */
    public void setExporter( UrlExporter exporter ) {
        exporter_ = exporter;
    }

    /**
     * Sets whether remote proxy should be generated for the local client
     * representing the local hub.
     * Default is not, since they are not very interesting to talk to.
     *
     * @param  useProxyHub  true iff the client representing the local hub 
     *         should be proxied remotely
     */
    public void setUseProxyHub( boolean useProxyHub ) {
        useProxyHub_ = useProxyHub;
    }

    /**
     * Prepares this manager for use by informing it about all its sibling
     * managers.  This must be done before the bridge can start operations.
     *
     * @param  allManagers  array of ProxyManagers including this one,
     *         one for each hub participating in the bridge
     */
    public void init( ProxyManager[] allManagers ) {

        // Store an array of all the other managers, excluding this one,
        // for later use.
        List remoteList = new ArrayList();
        int selfCount = 0;
        for ( int im = 0; im < allManagers.length; im++ ) {
            ProxyManager pm = allManagers[ im ];
            if ( pm == this ) {
                selfCount++;
            }
            else {
                remoteList.add( pm );
            }
        }
        if ( selfCount != 1 ) {
            throw new IllegalArgumentException( "Self not in list once" );
        }
        remoteManagers_ =
            (ProxyManager[]) remoteList.toArray( new ProxyManager[ 0 ] );
        nRemote_ = remoteManagers_.length;
        assert nRemote_ == allManagers.length - 1;
    }

    public String toString() {
        return localProfile_.toString();
    }

    /**
     * Returns the connection on the hub associated with a remote 
     * proxy manager which is the proxy for a given local client.
     *
     * @param  remoteManager  proxy manager for a remote bridged hub
     * @param  localClientId  client ID of a client registered with
     *         this manager's local hub
     * @return   proxy connection
     */
    private HubConnection getProxyConnection( ProxyManager remoteManager,
                                              String localClientId ) {
        HubConnection[] proxyConnections =
            (HubConnection[]) connectionMap_.get( localClientId );
        return proxyConnections == null
             ? null
             : proxyConnections[ getManagerIndex( remoteManager ) ];
    }

    /**
     * Deletes the record of the connection on the hub associated with
     * a remote proxy manager which is the proxy for a given local client.
     * This proxy can no longer be used.
     *
     * @param  remoteManager  proxy manager for a remote bridged hub
     * @param  localClientId  client ID of a client registered with
     *         this manager's local hub
     */
    private void removeProxyConnection( ProxyManager remoteManager,
                                        String localClientId ) {
        HubConnection[] proxyConnections =
            (HubConnection[]) connectionMap_.get( localClientId );
        if ( proxyConnections != null ) {
            proxyConnections[ getManagerIndex( remoteManager ) ] = null;
        }
    }

    /**
     * Returns the index by which this manager labels a given remote 
     * proxy manager.
     *
     * @param  remoteManager  manager to locate
     * @return   index of <code>remoteManager</code> in the list
     */
    private int getManagerIndex( ProxyManager remoteManager ) {
        return Arrays.asList( remoteManagers_ ).indexOf( remoteManager );
    }

    /**
     * Returns the metadata to use for the remote proxy of a local client.
     * This resembles the metadata of the local client itself, but may 
     * have some adjustments.
     *
     * @param  localClient   local client
     * @return   metadata to use for client's remote proxy
     */
    private Metadata getProxyMetadata( Client localClient ) {
        Metadata meta = localClient.getMetadata();
        if ( meta == null ) {
            return null;
        }
        else {
            meta = new Metadata( meta );
            if ( exporter_ != null ) {
                exporter_.exportMap( meta );
            }
            meta.setName( proxyName( meta.getName() ) );
            if ( meta.getIconUrl() != null ) {
                URL iconUrl = proxyIconUrl( meta.getIconUrl() );
                meta.setIconUrl( iconUrl == null ? null : iconUrl.toString() );
            }
            meta.put( "bridge.proxy.source", ProxyManager.this.toString() );
        }
        return meta;
    }

    /**
     * Returns the name to be used for a proxy client given its local name.
     *
     * @param   localName  local name
     * @return  proxy name
     */
    private String proxyName( String localName ) {
        return localName == null ? "(proxy)"
                                 : localName + " (proxy)";
    }

    /**
     * Returns the icon to be used for a proxy client given its local icon.
     *  
     * @param   localIconUrl  URL for local icon
     * @return  URL for proxy icon
     */
    private URL proxyIconUrl( URL localIconUrl ) {
        return localIconUrl != null
             ? iconAdjuster_.exportAdjustedIcon( localIconUrl )
             : localIconUrl;
    }

    /**
     * Returns the subscriptions to use for the remote proxy of a local client.
     * This resembles the subscriptions of the local client itself, but may
     * have some adjustments.
     * 
     * @param   localClient  local client
     * @return   subscriptions to use for client's remote proxy
     */
    private Subscriptions getProxySubscriptions( Client client ) {
        Subscriptions subs = client.getSubscriptions();
        if ( subs == null ) {
            return null;
        }
        else {

            // Remove subscriptions to most hub administrative MTypes.
            // These should not be delivered from the remote hub to the
            // local client, since the local client should only receive
            // such messages from its own hub.  Note this does not mean
            // that the local client will not be informed about changes
            // to clients on the remote hubs; this information will be
            // relayed by the local hub as a consequence of proxies from
            // other ProxyManagers making register/declare/etc calls
            // on this manager's local hub.
            subs = new Subscriptions( subs );
            subs.remove( "samp.hub.event.shutdown" );
            subs.remove( "samp.hub.event.register" );
            subs.remove( "samp.hub.event.unregister" );
            subs.remove( "samp.hub.event.metadata" );
            subs.remove( "samp.hub.event.subscriptions" );
            if ( exporter_ != null ) {
                exporter_.exportMap( subs );
            }
            return subs;
        }
    }

    /**
     * Called when this ProxyManager's connector has been disconnected 
     * (for whatever reason) from its local hub.
     * It makes sure that any proxies from other ProxyManagers to the local
     * hub are unregistered, so that no further bridge activity takes
     * place on the local hub.
     *
     * @param   isConnected  true for a connection; false for a disconnection
     */
    private void managerConnectionChanged( boolean isConnected ) {
        if ( ! isConnected ) {
            for ( int ir = 0; ir < nRemote_; ir++ ) {
                ProxyManager remoteManager = remoteManagers_[ ir ];
                int im = remoteManager.getManagerIndex( this );
                for ( Iterator it = remoteManager.connectionMap_.values()
                                                 .iterator();
                      it.hasNext(); ) {
                    HubConnection[] connections = (HubConnection[]) it.next();
                    if ( connections != null ) {
                        HubConnection connection = connections[ im ];
                        if ( connection != null ) {
                            connections[ im ] = null;
                            try {
                                connection.unregister();
                            }
                            catch ( SampException e ) {
                                logger_.info( "Unregister failed" );
                            }
                        }
                    }
                }
            }
        }
        else {
            // not expected
            assert false;
        }
    }

    /**
     * Invoked when a client is added to the local hub.
     *
     * @param  client  newly added client
     */
    private void localClientAdded( Client client ) {
        if ( ! isProxiedClient( client ) ) {
            return;
        }

        // Register a proxy for the new local client on all the remote hubs
        // in the bridge.
        Metadata meta = getProxyMetadata( client );
        Subscriptions subs = getProxySubscriptions( client );
        HubConnection[] proxyConnections = new HubConnection[ nRemote_ ];
        connectionMap_.put( client.getId(), proxyConnections );
        for ( int ir = 0; ir < nRemote_; ir++ ) {
            ProxyManager remoteManager = remoteManagers_[ ir ];
            try {

                // This synchronization is here so that the isProxy method
                // can work reliably.  isProxy may ask whether a client seen
                // on a remote hub is a proxy controlled by this one.
                // It can only ask after the registration has been done,
                // and the determination is synchronized on connectionMap_.
                // By synchronizing here, we can ensure that it can't ask
                // after the registration, but before the information has
                // been recorded in the connectionMap.
                final HubConnection proxyConnection;
                synchronized ( connectionMap_ ) {
                    proxyConnection = remoteManager.getProfile().register();
                    CallableClient callable = 
                        new ProxyCallableClient( client, proxyConnection,
                                                 remoteManager );
                    proxyConnection.setCallable( callable );
                    proxyConnections[ ir ] = proxyConnection;
                }
                if ( meta != null ) {
                    try {
                        proxyConnection.declareMetadata( meta );
                    }
                    catch ( SampException e ) {
                        logger_.warning( "proxy declareMetadata failed for "
                                       + client );
                    }
                }
                if ( subs != null ) {
                    try {
                        proxyConnection.declareSubscriptions( subs );
                    }
                    catch ( SampException e ) {
                        logger_.warning( "proxy declareSubscriptions failed "
                                       + "for " + client );
                    }
                }
            }
            catch ( SampException e ) {
                logger_.warning( "proxy registration failed for " + client );
            }
        }
    }

    /**
     * Invoked when a client is removed from the local hub.
     *
     * @param  client  recently removed client
     */
    private void localClientRemoved( Client client ) {
        if ( ! isProxiedClient( client ) ) {
            return;
        }

        // Remove all the proxies which were registered on remote hubs
        // on behalf of the removed client.
        HubConnection[] proxyConnections =
            (HubConnection[]) connectionMap_.remove( client.getId() );
        if ( proxyConnections != null ) {
            for ( int ir = 0; ir < nRemote_; ir++ ) {
                HubConnection connection = proxyConnections[ ir ];
                if ( connection != null ) {
                    try {
                        connection.unregister();
                    }
                    catch ( SampException e ) {
                        logger_.warning( "proxy unregister failed for "
                                       + client );
                    }
                }
            }
        }
    }

    /**
     * Invoked when information (metadata or subscriptions) have been 
     * updated for a client on the local hub.
     *
     * @param  client  updated client
     */
    private void localClientUpdated( Client client ) {
        if ( ! isProxiedClient( client ) ) {
            return;
        }

        // Cause each of the local client's proxies on remote hubs to
        // declare subscription/metadata updates appropriately.
        HubConnection[] proxyConnections =
            (HubConnection[]) connectionMap_.get( client.getId() );
        Metadata meta = getProxyMetadata( client );
        Subscriptions subs = getProxySubscriptions( client );
        if ( proxyConnections != null ) {
            for ( int ir = 0; ir < nRemote_; ir++ ) {
                HubConnection connection = proxyConnections[ ir ];
                if ( connection != null ) {
                    if ( meta != null ) {
                        try {
                            connection.declareMetadata( meta );
                        }
                        catch ( SampException e ) {
                            logger_.warning( "proxy declareMetadata failed "
                                           + "for " + client );
                        }
                    }
                    if ( subs != null ) {
                        try {
                            connection.declareSubscriptions( subs );
                        }
                        catch ( SampException e ) {
                            logger_.warning( "proxy declareSubscriptions "
                                           + "failed for " + client );
                        }
                    }
                }
            }
        }
    }

    /**
     * Determines whether a local client is a genuine third party client
     * which requires a remote proxy.  Will return false for clients which
     * are operating on behalf of this bridge, including the ProxyManager's
     * client tracking connection and any proxies controlled by remote
     * ProxyManagers.  Unless useProxyHub is true, will also return false 
     * for the hub client on remote hubs, since these are not very 
     * interesting to talk to.
     *
     * @param   client  local client
     * @param   true if <code>client</code> has or should have a proxy;
     *          false if it's an organ of the bridge administration
     */
    private boolean isProxiedClient( Client client ) {

        // Is it a client on the local hub that we want to exclude?
        try {
            if ( pmConnector_.isConnected() ) {
                HubConnection connection = pmConnector_.getConnection();
                if ( connection != null ) {
                    String clientId = client.getId();
                    RegInfo regInfo = connection.getRegInfo();
                    if ( clientId.equals( regInfo.getSelfId() ) ||
                         ( ! useProxyHub_ &&
                           clientId.equals( regInfo.getHubId() ) ) ) {
                        return false;
                    }
                }
            }
        }
        catch ( SampException e ) {
        }

        // Is it a proxy controlled by one of the remote managers?
        for ( int ir = 0; ir < nRemote_; ir++ ) {
            if ( remoteManagers_[ ir ].isProxy( client, ProxyManager.this ) ) {
                return false;
            }
        }

        // No, then it's a genuine local client requiring a proxy.
        return true;
    }

    /**
     * Determines whether a given local client is a proxy controlled by
     * a given remote ProxyManager.
     *
     * @param   client  local client
     * @param   remoteManager  remote proxy manager
     * @return   true iff <code>client</code> is one of 
     *           <code>remoteManager</code>'s proxies
     */
    private boolean isProxy( Client client, ProxyManager remoteManager ) {
        int ir = getManagerIndex( remoteManager );
        synchronized ( connectionMap_ ) {
            for ( Iterator it = connectionMap_.values().iterator();
                  it.hasNext(); ) {
                HubConnection[] proxyConnections = (HubConnection[]) it.next();
                if ( proxyConnections != null ) {
                    HubConnection proxyConnection = proxyConnections[ ir ];
                    if ( proxyConnection != null ) {
                        RegInfo proxyReg = proxyConnection.getRegInfo();
                        if ( proxyReg.getSelfId().equals( client.getId() ) ) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * CallableClient implementation used by remote proxy connections on
     * behalf of local clients.  This is the core of the proxy manager.
     * Callbacks received by the remote proxy client are tunnelled back 
     * to the local hub and forwarded by the local proxy of the remote 
     * sender client to the appropriate local non-proxy client.
     * Since local proxies are managed by other proxy managers 
     * (this one manages remote proxies of local clients)
     * this means getting the other proxy managers to do some of the work.
     */
    private class ProxyCallableClient implements CallableClient {
        private final String localClientId_;
        private final HubConnection remoteProxy_;
        private final ProxyManager remoteManager_;
        private final ProxyManager localManager_;

        /**
         * Constructor.
         *
         * @param   localClient  local client
         * @param   remoteProxy   hub connection to the remote hub for the proxy
         * @param   remoteManager  remote ProxyManager associated with the
         *          hub where this proxy is connected
         */
        ProxyCallableClient( Client localClient, HubConnection remoteProxy,
                             ProxyManager remoteManager ) {
            localClientId_ = localClient.getId();
            remoteProxy_ = remoteProxy;
            remoteManager_ = remoteManager;
            localManager_ = ProxyManager.this;
        }

        public void receiveNotification( String remoteSenderId, Message msg )
                throws SampException {

            // Forward the notification.
            if ( remoteManager_.exporter_ != null ) {
                msg = new Message( msg );
                remoteManager_.exporter_.exportMap( msg );
            }
            HubConnection localProxy = getLocalProxy( remoteSenderId );
            if ( localProxy != null ) {
                localProxy.notify( localClientId_, msg );
            }
            proxyProcessMessage( remoteSenderId, msg );
        }

        public void receiveCall( String remoteSenderId, String remoteMsgId,
                                 Message msg )
                throws SampException {

            // Choose a tag; use the message ID as its value.  
            // These things are different, but we are free to choose any
            // form for the tag, and we need something which will allow 
            // us to recover the message ID from it later.  
            // Making them identical is the easiest way to do that.
            String localMsgTag = remoteMsgId;

            // Forward the call.
            if ( remoteManager_.exporter_ != null ) {
                msg = new Message( msg );
                remoteManager_.exporter_.exportMap( msg );
            }
            HubConnection localProxy = getLocalProxy( remoteSenderId );
            if ( localProxy != null ) {
                localProxy.call( localClientId_, localMsgTag, msg );
            }
            else {
                ErrInfo errInfo = new ErrInfo();
                errInfo.setErrortxt( "Bridge can't forward message" );
                Client senderClient = 
                    (Client) remoteManager_.getManagerConnector()
                            .getClientMap().get( remoteSenderId );
                String usertxt = new StringBuffer()
                    .append( "Bridge can't forward message to recipient;\n" )
                    .append( "sender client " )
                    .append( senderClient )
                    .append( " has no proxy on remote hub" )
                    .toString();
                errInfo.setUsertxt( usertxt );
                    new ErrInfo( "Client " + remoteSenderId + " not present"
                               + " on other side of bridge" );
                remoteProxy_.reply( remoteMsgId,
                                    Response.createErrorResponse( errInfo ) );
            }
            proxyProcessMessage( remoteSenderId, msg );
        }

        public void receiveResponse( String remoteResponderId,
                                     String remoteMsgTag, Response response )
                throws SampException {

            // The message ID we need for forwarding is the one we encoded
            // (by identity) earlier in the tag.
            String localMsgId = remoteMsgTag;

            // Forward the reply appropriately.
            if ( remoteManager_.exporter_ != null ) {
                response = new Response( response );
                remoteManager_.exporter_.exportMap( response );
            }
            HubConnection localProxy = getLocalProxy( remoteResponderId );
            if ( localProxy != null ) {
                localProxy.reply( localMsgId, response );
            }
            else {

                // Should only happen if the proxy has been disconnected
                // between send and receive.
                logger_.warning( "Bridge can't forward response: "
                               + " missing proxy" );
            }
        }

        /**
         * Returns the hub connection for the proxy on the local hub
         * which corresponds to a given remote client.
         *
         * @param  remoteClientId  client ID of remote client
         * @return   hub connection for local proxy
         */
        private HubConnection getLocalProxy( String remoteClientId ) {
            return remoteManager_
                  .getProxyConnection( localManager_, remoteClientId );
        }

        /**
         * Performs housekeeping tasks for an incoming message if any.
         * This is in addition to forwarding the message to the client
         * for which we are proxying.
         *
         * @param   remoteSenderId  id of sending client on remote hub
         * @param   msg   message
         */
        private void proxyProcessMessage( String remoteSenderId, Message msg ) {
            String mtype = msg.getMType();
            boolean fromHub =
                remoteSenderId.equals( remoteProxy_.getRegInfo().getHubId() );
            if ( "samp.hub.disconnect".equals( mtype ) ) {
                if ( fromHub ) {
                    removeProxyConnection( remoteManager_, localClientId_ );
                }
                else {
                    logger_.warning( mtype + " from non-hub client "
                                   + remoteSenderId + " - ignored" );
                }
            }
        }
    }

    /**
     * TrackedClientSet implementation used by a Proxy Manager.
     * Apart from inheriting default behaviour, this triggers
     * calls to ProxyManager methods when there are status changes
     * to local clients.
     */
    private class ProxyManagerClientSet extends TrackedClientSet {

        /**
         * Constructor.
         */
        private ProxyManagerClientSet() {
            super();
        }

        public void addClient( Client client ) {
            super.addClient( client );
            localClientAdded( client );
        }

        public void removeClient( Client client ) {
            localClientRemoved( client );
            super.removeClient( client );
        }

        public void updateClient( Client client ) {
            super.updateClient( client );
            localClientUpdated( client );
        }

        public void setClients( Client[] clients ) {
            for ( Iterator it = getClientMap().entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                Client client = (Client) entry.getValue();
                localClientRemoved( client );
            }
            super.setClients( clients );
            for ( int i = 0; i < clients.length; i++ ) {
                Client client = clients[ i ];
                localClientAdded( client );
            }
        }
    }

    /**
     * Class which can turn a client's icon into the icon for the proxy of
     * the same client.  Some visually distinctive adjustment is made to
     * make it obvious from the icon that it's a proxy.
     */
    private class ProxyIconAdjuster extends IconAdjuster {

        /**
         * Constructor.
         */
        ProxyIconAdjuster() {
            super( server_.getServer(),
                   server_.getBasePath( "proxy" + "-" + localProfile_ ) );
        }

        public RenderedImage adjustImage( BufferedImage inImage ) {
            int w = inImage.getWidth();
            int h = inImage.getHeight();

            // Copy the image to a new image.  It would be possible to write
            // directly into the input BufferedImage, but this might not
            // have the correct image type, so could end up getting the
            // transparency wrong or something.
            BufferedImage outImage =
                new BufferedImage( w, h, BufferedImage.TYPE_4BYTE_ABGR );
            Graphics2D g2 = outImage.createGraphics();
            g2.drawImage( inImage, null, 0, 0 );

            // Slice off half of the image diagonally.
            int[] xs = new int[] { 0, w, w, };
            int[] ys = new int[] { h, h, 0, };
            Composite compos = g2.getComposite();
            g2.setComposite( AlphaComposite.Clear );
            g2.fillPolygon( xs, ys, 3 );
            g2.setComposite( compos );

            // Return the result.
            return outImage;
        }
    }
}
