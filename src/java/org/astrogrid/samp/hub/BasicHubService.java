package org.astrogrid.samp.hub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampMap;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * HubService implementation.
 * This is suitable for use with the SAMP Standard Profile, since most
 * methods require a <code>callerKey</code> object.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class BasicHubService implements HubService {

    private final KeyGenerator keyGen_;
    private final ClientIdGenerator idGen_;
    private final Map waiterMap_;
    private ClientSet clientSet_;
    private HubClient serviceClient_;
    private HubConnection serviceClientConnection_;
    private volatile boolean started_;
    private volatile boolean shutdown_;
    private static final char ID_DELIMITER = '_';
    private final Logger logger_ =
        Logger.getLogger( BasicHubService.class.getName() );

    /** The maximum timeout for a synchronous call permitted in seconds.
     *  Default is 43200 = 12 hours. */
    public static int MAX_TIMEOUT = 12 * 60 * 60;

    /** The maximum number of concurrently pending synchronous calls.
     *  Default is 100. */
    public static int MAX_WAITERS = 100;

    /**
     * Constructor.
     *
     * @param  random   random number generator used for message tags etc
     */
    public BasicHubService( Random random ) {

        // Prepare ID generators.
        keyGen_ = new KeyGenerator( "m:", 16, random );
        idGen_ = new ClientIdGenerator( "c" );

        // Prepare the data structure which keeps track of pending synchronous
        // calls.
        waiterMap_ = Collections.synchronizedMap( new HashMap() );
    }

    public void start() {

        // Prepare the data structure which keeps track of registered clients.
        clientSet_ = createClientSet();

        // Prepare and store the client object which represents the hub itself
        // (the one that apparently sends samp.hub.event.shutdown messages etc).
        serviceClient_ = createClient( "hub" );
        serviceClientConnection_ = createConnection( serviceClient_ );
        Metadata meta = new Metadata();
        meta.setName( "Hub" );
        try {
            meta.setIconUrl( UtilServer.getInstance()
                            .exportResource( "/org/astrogrid/samp/images/"
                                           + "hub.png" )
                            .toString() );
        }
        catch ( Throwable e ) {
            logger_.warning( "Can't set icon" );
        }
        meta.put( "author.name", "Mark Taylor" );
        meta.put( "author.mail", "m.b.taylor@bristol.ac.uk" );
        meta.setDescriptionText( getClass().getName() );
        serviceClient_.setMetadata( meta );
        HubCallableClient hubCallable =
            new HubCallableClient( serviceClient_, serviceClientConnection_ );
        serviceClient_.setCallable( hubCallable );
        serviceClient_.setSubscriptions( hubCallable.getSubscriptions() );
        clientSet_.add( serviceClient_ );

        // Ensure that things are tidied up (importantly, shutdown messages
        // get sent) in the case of a JVM termination.
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubService shutdown" ) {
            public void run() {
                shutdown();
            }
        } );
        started_ = true;
    }

   /**
     * Factory method used to create the client set used by this hub service.
     *
     * @return  client set 
     */
    protected ClientSet createClientSet() {
        return new BasicClientSet( getIdComparator() ) {
            public void add( HubClient client ) {
                assert client.getId().indexOf( ID_DELIMITER ) < 0;
                super.add( client );
            }
        };
    }

    /**
     * Factory method used to create all the client objects which will
     * be used by this hub service.
     *
     * @param   publicId  client public ID
     * @return   hub client
     */
    protected HubClient createClient( String publicId ) {
        return new HubClient( publicId );
    }

    /**
     * Returns a comparator which will order client IDs.
     * The ordering is in creation sequence.
     *
     * @return   public ID comparator
     */
    public Comparator getIdComparator() {
        return idGen_.getComparator();
    }

    /**
     * Returns the structure which keeps track of registered clients.
     *
     * @return   client set
     */
    public ClientSet getClientSet() {
        return clientSet_;
    }

    public HubConnection register() throws SampException {
        if ( ! started_ ) {
            throw new SampException( "Not started" );
        }
        HubClient client = createClient( idGen_.next() );
        assert client.getId().indexOf( ID_DELIMITER ) < 0;
        clientSet_.add( client );
        hubEvent( new Message( "samp.hub.event.register" )
                     .addParam( "id", client.getId() ) );
        return createConnection( client );
    }

    /**
     * Returns a new HubConnection for use by a given hub client.
     * The instance methods of the returned object delegate to similarly
     * named protected methods of this BasicHubService object.
     * These BasicHubService methods may therefore be overridden to
     * modify the behaviour of such returned connections.
     *
     * @param   caller   client requiring a connection
     * @return  connection whose methods may be called by or on behalf of 
     *          <code>caller</code>
     */
    protected HubConnection createConnection( final HubClient caller ) {
        final BasicHubService service = this;
        final RegInfo regInfo = new RegInfo();
        regInfo.put( RegInfo.HUBID_KEY, serviceClient_.getId() );
        regInfo.put( RegInfo.SELFID_KEY, caller.getId() );
        return new HubConnection() {
            public RegInfo getRegInfo() {
                return regInfo;
            }
            public void ping() throws SampException {
                if ( ! service.isHubRunning() ) {
                    throw new SampException( "Service is stopped" );
                }
            }
            public void unregister() throws SampException {
                checkCaller();
                service.unregister( caller );
            }
            public void setCallable( CallableClient callable )
                    throws SampException {
                checkCaller();
                service.setCallable( caller, callable );
            }
            public void declareMetadata( Map meta ) throws SampException {
                checkCaller();
                service.declareMetadata( caller, meta );
            }
            public Metadata getMetadata( String clientId )
                    throws SampException {
                checkCaller();
                return service.getMetadata( clientId );
            }
            public void declareSubscriptions( Map subs )
                    throws SampException {
                checkCaller();
                service.declareSubscriptions( caller, subs );
            }
            public Subscriptions getSubscriptions( String clientId )
                    throws SampException {
                checkCaller();
                return service.getSubscriptions( clientId );
            }
            public String[] getRegisteredClients() throws SampException {
                checkCaller();
                return service.getRegisteredClients( caller );
            }
            public Map getSubscribedClients( String mtype )
                    throws SampException {
                checkCaller();
                return service.getSubscribedClients( caller, mtype );
            }
            public void notify( String recipientId, Map message )
                    throws SampException {
                checkCaller();
                service.notify( caller, recipientId, message );
            }
            public String call( String recipientId, String msgTag,
                                Map message ) throws SampException {
                checkCaller();
                return service.call( caller, recipientId, msgTag, message );
            }
            public List notifyAll( Map message ) throws SampException {
                checkCaller();
                return service.notifyAll( caller, message );
            }
            public Map callAll( String msgTag, Map message )
                    throws SampException {
                checkCaller();
                return service.callAll( caller, msgTag, message );
            }
            public void reply( String msgId, Map response )
                    throws SampException {
                checkCaller();
                service.reply( caller, msgId, response );
            }
            public Response callAndWait( String recipientId, Map message,
                                         int timeout ) throws SampException {
                checkCaller();
                return service.callAndWait( caller, recipientId, message,
                                            timeout );
            }

            /**
             * Checks that this connection's client is able to make calls
             * on this connection.  If it is not, for instance if it has been
             * unregistered, an exception will be thrown.
             */
            private void checkCaller() throws SampException {
                if ( ! clientSet_.containsClient( caller ) ) {
                    throw new SampException( "Client not registered" );
                }
            }
        };
    }

    /**
     * Does the work for the <code>unregister</code> method of conections
     * registered with this service.
     *
     * @param   caller   client to unregister
     * @see   org.astrogrid.samp.client.HubConnection#unregister
     */
    protected void unregister( HubClient caller ) throws SampException {
        clientSet_.remove( caller );
        hubEvent( new Message( "samp.hub.event.unregister" )
                     .addParam( "id", caller.getId() ) );
    }

    /**
     * Does the work for the <code>setCallable</code> method of connections
     * registered with this service.
     *
     * @param   caller  client 
     * @param   callable  callable object
     * @see   org.astrogrid.samp.client.HubConnection#setCallable
     */
    protected void setCallable( HubClient caller, CallableClient callable )
            throws SampException {
        caller.setCallable( callable );
    }

    /**
     * Does the work for the <code>declareMetadata</code> method of connections
     * registered with this service.
     *
     * @param  caller  client
     * @param  meta   new metadata for client
     * @see  org.astrogrid.samp.client.HubConnection#declareMetadata
     */
    protected void declareMetadata( HubClient caller, Map meta )
            throws SampException {
        Metadata.asMetadata( meta ).check();
        caller.setMetadata( meta );
        hubEvent( new Message( "samp.hub.event.metadata" )
                     .addParam( "id", caller.getId() )
                     .addParam( "metadata", meta ) );
    }

    /**
     * Does the work for the <code>getMetadata</code> method of connections
     * registered with this service.
     *
     * @param  clientId  id of client being queried
     * @return   metadata for client
     * @see  org.astrogrid.samp.client.HubConnection#getMetadata
     */
    protected Metadata getMetadata( String clientId ) throws SampException {
        return getClient( clientId ).getMetadata();
    }

    /**
     * Does the work for the <code>declareSubscriptions</code> method of
     * connections registered with this service.
     *
     * @param   caller  client
     * @param  subscriptions  new subscriptions for client
     * @see  org.astrogrid.samp.client.HubConnection#declareSubscriptions
     */
    protected void declareSubscriptions( HubClient caller, Map subscriptions )
            throws SampException {
        if ( caller.isCallable() ) {
            Subscriptions.asSubscriptions( subscriptions ).check();
            caller.setSubscriptions( subscriptions );
            hubEvent( new Message( "samp.hub.event.subscriptions" )
                         .addParam( "id", caller.getId() )
                         .addParam( "subscriptions", subscriptions ) );
        }
        else {
            throw new SampException( "Client is not callable" );
        }
    }

    /** 
     * Does the work for the <code>getSubscriptions</code> method of connections
     * registered with this service.
     *
     * @param   clientId  id of client being queried
     * @return   subscriptions for client
     * @see  org.astrogrid.samp.client.HubConnection#getSubscriptions
     */
    protected Subscriptions getSubscriptions( String clientId )
            throws SampException {
        return getClient( clientId ).getSubscriptions();
    }

    /**
     * Does the work for the <code>getRegisteredClients</code> method of
     * connections registered with this service.
     *
     * @param  caller  calling client
     * @return   array of registered client IDs excluding <code>caller</code>'s
     * @see  org.astrogrid.samp.client.HubConnection#getRegisteredClients
     */
    protected String[] getRegisteredClients( HubClient caller )
            throws SampException {
        HubClient[] clients = clientSet_.getClients();
        List idList = new ArrayList( clients.length );
        for ( int ic = 0; ic < clients.length; ic++ ) {
            if ( ! clients[ ic ].equals( caller ) ) {
                idList.add( clients[ ic ].getId() );
            }
        }
        return (String[]) idList.toArray( new String[ 0 ] );
    }

    /**
     * Does the work for the <code>getSubscribedClients</code> method of
     * connections registered with this service.
     *
     * @param  caller  calling client
     * @param  mtype   message type
     * @return   map in which the keys are the public IDs of clients
     *           subscribed to <code>mtype</code>
     * @see  org.astrogrid.samp.client.HubConnection#getSubscribedClients
     */
    protected Map getSubscribedClients( HubClient caller, String mtype )
            throws SampException {
        HubClient[] clients = clientSet_.getClients();
        Map subMap = new TreeMap(); 
        for ( int ic = 0; ic < clients.length; ic++ ) {
            HubClient client = clients[ ic ];
            if ( ! client.equals( caller ) ) {
                Map sub = client.getSubscriptions().getSubscription( mtype );
                if ( sub != null ) {
                    subMap.put( client.getId(), sub );
                }
            }
        }
        return subMap;
    }

    /**
     * Does the work for the <code>notify</code> method of connections
     * registered with this service.
     *
     * @param   caller  calling client
     * @param   recipientId  public ID of client to receive message
     * @param   message  message
     * @see  org.astrogrid.samp.client.HubConnection#notify
     */
    protected void notify( HubClient caller, String recipientId, Map message )
            throws SampException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            try {
                recipient.getCallable()
                         .receiveNotification( caller.getId(), msg );
            }
            catch ( SampException e ) {
                throw e;
            }
            catch ( Exception e ) {
                throw new SampException( e.getMessage(), e );
            }
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
    }

    /**
     * Does the work for the <code>call</code> method of connections
     * registered with this service.
     *
     * @param   caller  calling client
     * @param   recipientId  client ID of recipient
     * @param   msgTag  message tag
     * @param   message  message
     * @return  message ID 
     * @see   org.astrogrid.samp.client.HubConnection#call
     */
    protected String call( HubClient caller, String recipientId, String msgTag,
                           Map message )
            throws SampException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        String msgId = MessageId.encode( caller, msgTag, false );
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            try {
                recipient.getCallable()
                         .receiveCall( caller.getId(), msgId, msg );
            }
            catch ( SampException e ) {
                throw e;
            }
            catch ( Exception e ) {
                throw new SampException( e.getMessage(), e );
            }
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
        return msgId;
    }

    /**
     * Does the work for the <code>notifyAll</code> method of connections
     * registered with this service.
     *
     * @param   caller   calling client
     * @param   message   message
     * @return  list of public IDs for clients to which the notify will be sent
     * @see  org.astrogrid.samp.client.HubConnection#notifyAll
     */
    protected List notifyAll( HubClient caller, Map message )
            throws SampException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        HubClient[] recipients = clientSet_.getClients();
        List sentList = new ArrayList();
        for ( int ic = 0; ic < recipients.length; ic++ ) {
            HubClient recipient = recipients[ ic ];
            if ( recipient != caller && recipient.isSubscribed( mtype ) ) {
                try {
                    recipient.getCallable()
                             .receiveNotification( caller.getId(), msg );
                    sentList.add( recipient.getId() );
                }
                catch ( Exception e ) {
                    logger_.log( Level.WARNING, 
                                 "Notification " + caller + " -> " + recipient
                               + " failed: " + e, e );
                }
            }
        }
        return sentList;
    }

    /**
     * Does the work for the <code>call</code> method of connections
     * registered with this service.
     *
     * @param   caller  calling client
     * @param   msgTag  message tag
     * @param   message  message
     * @return   publicId-&gt;msgId map for clients to which an attempt to
     *           send the call will be made
     * @see   org.astrogrid.samp.client.HubConnection#callAll
     */
    protected Map callAll( HubClient caller, String msgTag, Map message )
            throws SampException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        String msgId = MessageId.encode( caller, msgTag, false );
        HubClient[] recipients = clientSet_.getClients();
        Map sentMap = new HashMap();
        for ( int ic = 0; ic < recipients.length; ic++ ) {
            HubClient recipient = recipients[ ic ];
            if ( recipient != caller && recipient.isSubscribed( mtype ) ) {
                try {
                    recipient.getCallable()
                             .receiveCall( caller.getId(), msgId, msg );
                }
                catch ( SampException e ) {
                    throw e;
                }
                catch ( Exception e ) {
                    throw new SampException( e.getMessage(), e );
                }
                sentMap.put( recipient.getId(), msgId );
            }
        }
        return sentMap;
    }

    /**
     * Does the work for the <code>reply</code> method of connections
     * registered with this service.
     *
     * @param   caller  calling client
     * @param   msgIdStr   message ID
     * @param   resp  response to forward
     * @see   org.astrogrid.samp.client.HubConnection#reply
     */
    protected void reply( HubClient caller, String msgIdStr, Map resp )
            throws SampException {
        Response response = Response.asResponse( resp );
        response.check();
        MessageId msgId = MessageId.decode( msgIdStr );
        HubClient sender = getClient( msgId.getSenderId() );
        String senderTag = msgId.getSenderTag();

        // If we can see from the message ID that it was originally sent
        // synchronously, take steps to place the response in the map of
        // waiting messages where it will get picked up and returned to
        // the sender as a callAndWait return value.
        if ( msgId.isSynch() ) {
            synchronized ( waiterMap_ ) {
                if ( waiterMap_.containsKey( msgId ) ) {
                    if ( waiterMap_.get( msgId ) == null ) {
                         waiterMap_.put( msgId, response );
                         waiterMap_.notifyAll();
                    }
                    else {
                        throw new SampException(
                            "Response ignored - you've already sent one" );
                    }
                }
                else {
                    throw new SampException(
                        "Response ignored - synchronous call timed out" );
                }
            }
        }

        // Otherwise, just pass it to the sender using a callback.
        else {
            try {
                sender.getCallable()
                      .receiveResponse( caller.getId(), senderTag, response );
            }
            catch ( SampException e ) {
                throw e;
            }
            catch ( Exception e ) {
                throw new SampException( e.getMessage(), e );
            }
        }
    }

    /**
     * Does the work for the <code>callAndWait</code> method of connections
     * registered with this service.
     *
     * @param   caller  calling client
     * @param   recipientId  client ID of recipient
     * @param   message  message
     * @param   timeout  timeout in seconds
     * @return   response  response
     * @see   org.astrogrid.samp.client.HubConnection#callAndWait
     */
    protected Response callAndWait( HubClient caller, String recipientId,
                                    Map message, int timeout )
            throws SampException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        MessageId hubMsgId =
            new MessageId( caller.getId(), keyGen_.next(), true );
        long start = System.currentTimeMillis();
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            synchronized ( waiterMap_ ) {

                // If the number of pending synchronous calls exceeds the 
                // permitted maximum, remove the oldest calls until there is
                // space for the new one.
                if ( MAX_WAITERS > 0 && waiterMap_.size() >= MAX_WAITERS ) {
                    int excess = waiterMap_.size() - MAX_WAITERS + 1;
                    List keyList = new ArrayList( waiterMap_.keySet() );
                    Collections.sort( keyList, MessageId.AGE_COMPARATOR );
                    logger_.warning( "Pending synchronous calls exceeds limit "
                                   + MAX_WAITERS + " - giving up on " + excess
                                   + " oldest" );
                    for ( int ie = 0; ie < excess; ie++ ) {
                        Object removed = waiterMap_.remove( keyList.get( ie ) );
                        assert removed != null;
                    }
                    waiterMap_.notifyAll();
                }

                // Place an entry for this synchronous call in the waiterMap.
                waiterMap_.put( hubMsgId, null );
            }

            // Make the call asynchronously to the receiver.
            try {
                recipient.getCallable()
                         .receiveCall( caller.getId(), hubMsgId.toString(),
                                       msg );
            }
            catch ( SampException e ) {
                throw e;
            }
            catch ( Exception e ) {
                throw new SampException( e.getMessage(), e );
            }

            // Wait until either the timeout expires, or the response to the
            // message turns up in the waiter map (placed there on another
            // thread by this the reply() method).
            timeout = Math.min( Math.max( 0, timeout ),
                                Math.max( 0, MAX_TIMEOUT ) );
            long finish = timeout > 0
                        ? System.currentTimeMillis() + timeout * 1000
                        : Long.MAX_VALUE;  // 3e8 years
            synchronized ( waiterMap_ ) {
                while ( waiterMap_.containsKey( hubMsgId ) &&
                        waiterMap_.get( hubMsgId ) == null &&
                        System.currentTimeMillis() < finish ) {
                    long millis = finish - System.currentTimeMillis();
                    if ( millis > 0 ) {
                        try {
                            waiterMap_.wait( millis );
                        }
                        catch ( InterruptedException e ) {
                            throw new SampException( "Wait interrupted", e );
                        }
                    }
                }

                // If the response is there, return it to the caller of this
                // method (the sender of the message).
                if ( waiterMap_.containsKey( hubMsgId ) ) {
                    Response response =
                        (Response) waiterMap_.remove( hubMsgId );
                    if ( response != null ) {
                        return response;
                    }

                    // Otherwise, it must have timed out.  Exit with an error.
                    else {
                        assert System.currentTimeMillis() >= finish;
                        String millis =
                            Long.toString( System.currentTimeMillis() - start );
                        String emsg = new StringBuffer()
                            .append( "Synchronous call timeout after " )
                            .append( millis.substring( 0,
                                                       millis.length() - 3 ) )
                            .append( '.' )
                            .append( millis.substring( millis.length() - 3 ) )
                            .append( '/' )
                            .append( timeout )
                            .append( " sec" )
                            .toString();
                        throw new SampException( emsg );
                    }
                }
                else {
                    throw new SampException(
                        "Synchronous call aborted"
                      + " - server load exceeded maximum of " + MAX_WAITERS
                      + "?" );
                }
            }
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
    }

    /**
     * Returns the HubConnection object which represents the hub itself.
     * This is the one which apparently sends samp.hub.event.shutdown messages
     * etc.
     *
     * @return  hub service object
     */
    public HubConnection getServiceConnection() {
        return serviceClientConnection_;
    }

    public void disconnect( String clientId, String reason )
            throws SampException {
        if ( clientId.equals( serviceClient_.getId() ) ) {
            throw new SampException( "Refuse to disconnect "
                                   + "the hub client itself" );
        }
        HubClient client = clientSet_.getFromPublicId( clientId );
        String mtype = "samp.hub.disconnect";
        if ( client.isSubscribed( mtype ) ) {
            Message msg = new Message( mtype );
            if ( reason != null && reason.length() > 0 ) {
                msg.addParam( "reason", reason );
            }
            try {
                notify( serviceClient_, clientId, msg );
            }
            catch ( SampException e ) {
                logger_.log( Level.INFO,
                             mtype + " to " + client + " failed", e );
            }
        }
        clientSet_.remove( client );
        hubEvent( new Message( "samp.hub.event.unregister" )
                 .addParam( "id", clientId ) );
    }

    public synchronized boolean isHubRunning() {
        return started_ && ! shutdown_;
    }

    public synchronized void shutdown() {
        if ( ! shutdown_ ) {
            shutdown_ = true;
            hubEvent( new Message( "samp.hub.event.shutdown" ) );
            serviceClientConnection_ = null;
        }
    }

    /**
     * Broadcast an event message to all subscribed clients.
     * The sender of this message is the hub application itself.
     *
     * @param  msg  message to broadcast
     */
    private void hubEvent( Message msg ) {
        try {
            notifyAll( serviceClient_, msg );
        }
        catch ( SampException e ) {
            assert false;
        }
    }

    /**
     * Returns the client object corresponding to a public client ID.
     * If no such client is registered, throw an exception.
     *
     * @param   id  client public id
     * @return  HubClient object
     */
    private HubClient getClient( String id ) throws SampException {
        HubClient client = clientSet_.getFromPublicId( id );
        if ( client != null ) {
            return client;
        }
        else if ( idGen_.hasUsed( id ) ) {
            throw new SampException( "Client " + id
                                   + " is no longer registered" );
        }
        else {
            throw new SampException( "No registered client with ID \""
                                   + id + "\"" );
        }
    }

    /**
     * Encapsulates information about a MessageId.
     * A message ID can be represented as a string, but encodes information
     * which can be retrieved later.
     */
    private static class MessageId {

        private final String senderId_;
        private final String senderTag_;
        private final boolean isSynch_;
        private final long birthday_;

        private static final String T_SYNCH_FLAG = "S";
        private static final String F_SYNCH_FLAG = "A";
        private static final int CHECK_SEED = (int) System.currentTimeMillis();
        private static final int CHECK_LENG = 4;
        private static final Comparator AGE_COMPARATOR = new Comparator() {
            public int compare( Object o1, Object o2 ) {
                return (int) (((MessageId) o1).birthday_ -
                              ((MessageId) o2).birthday_);
            }
        };

        /**
         * Constructor.
         *
         * @param  senderId  client id of the message sender
         * @param  senderTag  msgTag provided by the sender
         * @param  isSynch   whether the message was sent synchronously or not
         */
        public MessageId( String senderId, String senderTag, boolean isSynch ) {
            senderId_ = senderId;
            senderTag_ = senderTag;
            isSynch_ = isSynch;
            birthday_ = System.currentTimeMillis();
        }

        /**
         * Returns the sender's public client id.
         *
         * @return  sender's id
         */
        public String getSenderId() {
            return senderId_;
        }

        /**
         * Returns the msgTag attached to the message by the sender.
         *
         * @return   msgTag
         */
        public String getSenderTag() {
            return senderTag_;
        }

        /**
         * Returns whether the message was sent synchronously.
         *
         * @return  true iff message was sent using callAndWait
         */
        public boolean isSynch() {
            return isSynch_;
        }

        public int hashCode() {
            return checksum( senderId_, senderTag_, isSynch_ ).hashCode();
        }

        public boolean equals( Object o ) {
            if ( o instanceof MessageId ) {
                MessageId other = (MessageId) o;
                return this.senderId_.equals( other.senderId_ )
                    && this.senderTag_.equals( other.senderTag_ )
                    && this.isSynch_ == other.isSynch_;
            }
            else {
                return false;
            }
        }

        /**
         * Returns the string representation of this MessageId.
         *
         * @return  message ID string
         */
        public String toString() {
            Object checksum = checksum( senderId_, senderTag_, isSynch_ );
            return new StringBuffer()
                  .append( senderId_ )
                  .append( ID_DELIMITER )
                  .append( isSynch_ ? T_SYNCH_FLAG : F_SYNCH_FLAG )
                  .append( ID_DELIMITER )
                  .append( checksum )
                  .append( ID_DELIMITER )
                  .append( senderTag_ )
                  .toString();
        }

        /**
         * Decodes a msgId string to return the corresponding MessageId object.
         * This is the opposite of the {@link #toString} method.
         *
         * @param  msgId  string representation of message ID
         * @return   new MessageId object
         */
        public static MessageId decode( String msgId ) throws SampException {
            int delim1 = msgId.indexOf( ID_DELIMITER );
            int delim2 = msgId.indexOf( ID_DELIMITER, delim1 + 1 );
            int delim3 = msgId.indexOf( ID_DELIMITER, delim2 + 1 );
            if ( delim1 < 0 || delim2 < 0 || delim3 < 0 ) {
                throw new SampException( "Badly formed message ID " + msgId );
            }
            String senderId = msgId.substring( 0, delim1 );
            String synchFlag = msgId.substring( delim1 + 1, delim2 );
            String checksum = msgId.substring( delim2 + 1, delim3 );
            String senderTag = msgId.substring( delim3 + 1 );
            boolean isSynch;
            if ( T_SYNCH_FLAG.equals( synchFlag ) ) {
                isSynch = true;
            }
            else if ( F_SYNCH_FLAG.equals( synchFlag ) ) {
                isSynch = false;
            }
            else {
                throw new SampException( "Badly formed message ID "
                                       + msgId + " (synch flag)" );
            }
            if ( ! checksum( senderId, senderTag, isSynch )
                  .equals( checksum ) ) {
                throw new SampException( "Bad message ID checksum" );
            }
            MessageId idObj = new MessageId( senderId, senderTag, isSynch );
            assert idObj.toString().equals( msgId );
            return idObj;
        }

        /**
         * Returns a message ID string corresponding to the arguments.
         *
         * @param   sender   sender client
         * @param   senderTag  msgTag attached by sender
         * @param   isSynch  whether message was sent synchronously
         * @return  string representation of message ID
         */
        public static String encode( HubClient sender, String senderTag,
                                     boolean isSynch ) {
            return new MessageId( sender.getId(), senderTag, isSynch )
                  .toString();
        }

        /**
         * Returns a checksum string which is a hash of the given arguments.
         *
         * @param  senderId  public client id of sender
         * @param   senderTag  msgTag attached by sender
         * @param   isSynch  whether message was sent synchronously
         * @return  checksum string
         */
        private static String checksum( String senderId, String senderTag,
                                        boolean isSynch ) {
            int sum = CHECK_SEED;
            sum = 23 * sum + senderId.hashCode();
            sum = 23 * sum + senderTag.hashCode();
            sum = 23 * sum + ( isSynch ? 3 : 5 );
            String check = Integer.toHexString( sum );
            check =
                check.substring( Math.max( 0, check.length() - CHECK_LENG ) );
            while ( check.length() < CHECK_LENG ) {
                check = "0" + check;
            }
            assert check.length() == CHECK_LENG;
            return check;
        }
    }

    /**
     * Generates client public IDs.
     * These must be unique, but don't need to be hard to guess.
     */
    private static class ClientIdGenerator {
        private int iseq_;
        private final String prefix_;
        private final Comparator comparator_;

        /**
         * Constructor.
         *
         * @param  prefix  prefix for all generated ids
         */
        public ClientIdGenerator( String prefix ) {
            prefix_ = prefix;

            // Prepare a comparator which will order the keys generated here
            // in sequence of generation.
            comparator_ = new Comparator() {
                public int compare( Object o1, Object o2 ) {
                    String s1 = o1.toString();
                    String s2 = o2.toString();
                    Integer i1 = getIndex( s1 );
                    Integer i2 = getIndex( s2 );
                    if ( i1 == null && i2 == null ) {
                        return s1.compareTo( s2 );
                    }
                    else if ( i1 == null ) {
                        return +1;
                    }
                    else if ( i2 == null ) {
                        return -1;
                    }
                    else {
                        return i1.intValue() - i2.intValue();
                    }
                } 
            };
        }

        /**
         * Returns the next unused id.
         *
         * @return  next id
         */
        public synchronized String next() {
            return prefix_ + Integer.toString( ++iseq_ );
        }

        /**
         * Indicates whether a given client ID has previously been dispensed
         * by this object.
         *
         * @param  id  id to test
         * @return  true iff id has been returned by a previous call of
         *          <code>next</code>
         */
        public boolean hasUsed( String id ) {
            Integer ix = getIndex( id );
            return ix != null && ix.intValue() <= iseq_;
        }

        /**
         * Returns an Integer giving the sequence index of the given id string.
         * If <code>id</code> does not look like a string generated by this
         * object, null is returned.
         *
         * @param   id  identifier to test
         * @return   object containing sequence index of <code>id</code>,
         *           or null
         */
        private Integer getIndex( String id ) {
            if ( id.startsWith( prefix_ ) ) {
                try {
                    int iseq =
                        Integer.parseInt( id.substring( prefix_.length() ) );
                    return new Integer( iseq );
                }
                catch ( NumberFormatException e ) {
                    return null;
                }
            }
            else {
                return null;
            }
        }

        /**
         * Returns a comparator which will order the IDs generated by this
         * object in generation sequence.
         *
         * @return  id comparator
         */
        public Comparator getComparator() {
            return comparator_;
        }
    }
}
