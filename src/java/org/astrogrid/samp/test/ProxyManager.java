package org.astrogrid.samp.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
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

 // local means the target hub for this manager.
 // remote is the hub this proxy is connected to.

class ProxyManager {

    private final ClientProfile localProfile_;
    private final HubConnector pmConnector_;
    private final Map connectionMap_;  // local client ID -> HubConnection[]
    private final Map tagMap_;
    private ProxyManager[] remoteManagers_;
    private int nRemote_;

    private static final Logger logger_ =
        Logger.getLogger( ProxyManager.class.getName() );

    public ProxyManager( ClientProfile localProfile ) {
        localProfile_ = localProfile;
        pmConnector_ =
            new HubConnector( localProfile, new ProxyManagerClientSet() );
        connectionMap_ = Collections.synchronizedMap( new HashMap() );
        tagMap_ = Collections.synchronizedMap( new HashMap() );

        Metadata meta = new Metadata();
        meta.setName( "bridge" );
        meta.setDescriptionText( "Bridge between hubs" );
        meta.put( "author.name", "Mark Taylor" );
        meta.put( "author.email", "m.b.taylor@bristol.ac.uk" );
        pmConnector_.declareMetadata( meta );
        Subscriptions subs = pmConnector_.computeSubscriptions();
        pmConnector_.declareSubscriptions( subs );
    }

    public ClientProfile getProfile() {
        return localProfile_;
    }

    public HubConnector getManagerConnector() {
        return pmConnector_;
    }

    public void init( ProxyManager[] allManagers ) {
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

    public HubConnection getProxyConnection( ProxyManager manager,
                                             String clientId ) {
        HubConnection[] proxyConnections =
            (HubConnection[]) connectionMap_.get( clientId );
        return proxyConnections == null
             ? null
             : proxyConnections[ getManagerIndex( manager ) ];
    }

    public String toString() {
        return localProfile_.toString();
    }

    private int getManagerIndex( ProxyManager manager ) {
        return Arrays.asList( remoteManagers_ ).indexOf( manager );
    }

    private Metadata getProxyMetadata( Client client ) {
        Metadata meta = client.getMetadata();
        if ( meta == null ) {
            return null;
        }
        else {
            meta = new Metadata( meta );
            meta.setName( meta.getName() + " (proxy)" );
            meta.put( "bridge.proxy.source", ProxyManager.this.toString() );
        }
        return meta;
    }

    private Subscriptions getProxySubscriptions( Client client ) {
        Subscriptions subs = client.getSubscriptions();
        if ( subs == null ) {
            return null;
        }
        else {
            subs = new Subscriptions( subs );
            subs.remove( "samp.hub.event.shutdown" );
            subs.remove( "samp.hub.event.register" );
            subs.remove( "samp.hub.event.unregister" );
            subs.remove( "samp.hub.event.metadata" );
            subs.remove( "samp.hub.event.subscriptions" );
            return subs;
        }
    }

    private void localClientAdded( Client client ) {
        if ( ignoreLocalClient( client ) ) {
            return;
        }
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

    private void localClientRemoved( Client client ) {
        if ( ignoreLocalClient( client ) ) {
            return;
        }
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

    private void localClientUpdated( Client client ) {
        if ( ignoreLocalClient( client ) ) {
            return;
        }
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

    private boolean ignoreLocalClient( Client client ) {

        // Is it the ProxyManager's connection on the local hub?
        try {
            if ( pmConnector_.isConnected() &&
                 pmConnector_.getConnection().getRegInfo().getSelfId()
                             .equals( client.getId() ) ) {
                return true;
            }
        }
        catch ( SampException e ) {
        }

        // Is it a proxy working for one of the remote managers?
        for ( int ir = 0; ir < nRemote_; ir++ ) {
            if ( remoteManagers_[ ir ].isProxy( client, ProxyManager.this ) ) {
                return true;
            }
        }

        // No, then it's a genuine local client.
        return false;
    }

    private boolean isProxy( Client client, ProxyManager remoteManager ) {
        int ir = getManagerIndex( remoteManager );
        synchronized ( connectionMap_ ) {
            for ( Iterator it = connectionMap_.values().iterator();
                  it.hasNext(); ) {
                HubConnection[] proxyConnections = (HubConnection[]) it.next();
                if ( proxyConnections != null ) {
                    RegInfo proxyReg = proxyConnections[ ir ].getRegInfo();
                    if ( proxyReg.getSelfId().equals( client.getId() ) ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void storeTagId( String clientId, String msgTag, String msgId ) {
        tagMap_.put( getTagKey( clientId, msgTag ), msgId );
    } 

    private String tagToId( String clientId, String msgTag ) {
        return (String) tagMap_.remove( getTagKey( clientId, msgTag ) );
    }

    private Object getTagKey( String clientId, String msgTag ) {
        return clientId + "\n" + msgTag;
    }

 // local means the target hub for this manager.
 // remote is the hub this proxy is connected to.

    private class ProxyCallableClient implements CallableClient {
        private final String localClientId_;
        private final HubConnection remoteProxy_;
        private final ProxyManager remoteManager_;
        private final ProxyManager localManager_;

        ProxyCallableClient( Client localClient, HubConnection remoteProxy,
                             ProxyManager remoteManager ) {
            localClientId_ = localClient.getId();
            remoteProxy_ = remoteProxy;
            remoteManager_ = remoteManager;
            localManager_ = ProxyManager.this;
        }

        public void receiveCall( String remoteSenderId, String remoteMsgId,
                                 Message msg )
                throws SampException {
            String localMsgTag = localManager_.pmConnector_.createTag( null );
            HubConnection localSenderConnection =
                getLocalProxy( remoteSenderId );
            localSenderConnection.call( localClientId_, localMsgTag, msg );
            localManager_.storeTagId( localClientId_, localMsgTag,
                                      remoteMsgId );
        }

        public void receiveNotification( String remoteSenderId, Message msg )
                throws SampException {
            HubConnection localSenderConnection =
                getLocalProxy( remoteSenderId );
            localSenderConnection.notify( localClientId_, msg );
        }

        public void receiveResponse( String remoteResponderId,
                                     String remoteMsgTag, Response response )
                throws SampException {
            HubConnection localResponderConnection =
                getLocalProxy( remoteResponderId );
            String localMsgId =
                remoteManager_.tagToId( remoteResponderId, remoteMsgTag );
            localResponderConnection.reply( localMsgId, response );
        }

        private HubConnection getLocalProxy( String remoteClientId ) {
            return remoteManager_
                  .getProxyConnection( localManager_, remoteClientId );
        }
    }

    private class ProxyManagerClientSet extends TrackedClientSet {

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

    private class ProxyManagerClient implements Client {
        private final Client base_;

        ProxyManagerClient( Client base ) {
            base_ = base;
        }

        public String getId() {
            return base_.getId();
        }

        public Metadata getMetadata() {
            return base_.getMetadata();
        }

        public Subscriptions getSubscriptions() {
            return base_.getSubscriptions();
        }

        public int hashCode() {
            return base_.hashCode();
        }

        public boolean equals( Object o ) {
            if ( o instanceof ProxyManagerClient ) {
                ProxyManagerClient other = (ProxyManagerClient) o;
                return this.base_.equals( other.base_ );
            }
            else {
                return false;
            }
        }

        public String toString() {
            return localProfile_ + ":" + base_.toString();
        }
    }
}
