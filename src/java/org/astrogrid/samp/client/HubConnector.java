package org.astrogrid.samp.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;

/**
 * Manages a client's connection to SAMP hubs.
 * Normally SAMP client applications will use one instance of this class
 * for as long as they are running.
 * It provides the following services:
 * <ul>
 * <li>Keeps track of other registered clients
 * <li>Keeps track of hubs shutting down and starting up
 * <li>Manages client metadata and subscription information across
 *     hub reconnections
 * <li>Facilitates provision of callback services by the client
 * <li>Implements simple MTypes such as <code>samp.app.ping</code>.
 * <li>Optionally looks out for hubs starting up and connects automatically
 *     when they do
 * </ul>
 *
 * <p>This object provides a {@link #getConnection} method which provides
 * the currently active {@link HubConnection} object if one exists or can be
 * acquired.  The <code>HubConnection</code> can be used for direct calls
 * on the running hub, but in some cases similar methods with additional
 * functionality exist in this class:
 * <dl>
 * <dt>{@link #declareMetadata declareMetadata}
 * <dt>{@link #declareSubscriptions declareSubscriptions}
 * <dd>These methods not only make the relevant declarations to the
 *     existing hub connection, if one exists, but will retain the
 *     metadata and subscriptions information and declare them to
 *     other connections if the hub connection is terminated and
 *     restarted (with either the same or a different hub)
 *     over the lifetime of this object.
 *     </dd>
 * <dt>{@link #callAndWait callAndWait}
 * <dd>Provides identical semantics to the similarly named
 *     <code>HubConnection</code> method, but communicates with the hub
 *     asynchronously and fakes the synchrony at the client end.
 *     This is more robust and almost certainly a better idea.
 *     </dd>
 * <dt>{@link #call call}
 * <dt>{@link #callAll callAll}
 * <dd>Convenience methods to make asynchronous calls without having to 
 *     worry about registering handlers which match up message tags.
 *     </dd>
 *
 * </dl>
 *
 * <p>It is good practice to call {@link #setActive setActive(false)}
 * when this object is finished with; however if it is not called
 * explicitly, any open connection will unregister itself on
 * object finalisation or JVM termination, as long as the JVM shuts
 * down cleanly.
 *
 * <h3>Examples</h3>
 * Here is an example of what use of this class might look like:
 * <pre>
 *   // Construct a connector
 *   ClientProfile profile = StandardClientProfile.getInstance();
 *   HubConnector conn = new HubConnector(profile)
 *
 *   // Configure it with metadata about this application
 *   Metadata meta = new Metadata();
 *   meta.setName("Foo");
 *   meta.setDescriptionText("Application that does stuff");
 *   conn.declareMetadata(meta);
 *
 *   // Prepare to receive messages with specific MType(s)
 *   conn.addMessageHandler(new AbstractMessageHandler("stuff.do") {
 *       public Map processCall(HubConnection c, String senderId, Message msg) {
 *           // do stuff
 *       }
 *   });
 *
 *   // This step required even if no custom message handlers added.
 *   conn.declareSubscriptions(conn.computeSubscriptions());
 *
 *   // Keep a look out for hubs if initial one shuts down
 *   conn.setAutoconnect(10);
 *
 *   // Broadcast a message
 *   conn.getConnection().notifyAll(new Message("stuff.event.doing"));
 * </pre>
 *
 * <p>A real example, including use of the GUI hooks, can be found in the
 * {@link org.astrogrid.samp.gui.HubMonitor} client source code.
 *
 * <h3>Backwards Compatibility Note</h3>
 * This class does less than it did in earlier versions;
 * the functionality which is no longer here can now be found in the 
 * {@link org.astrogrid.samp.gui.GuiHubConnector} class instead.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class HubConnector {

    private final ClientProfile profile_;
    private final TrackedClientSet clientSet_;
    private final List messageHandlerList_;
    private final List responseHandlerList_;
    private final ConnectorCallableClient callable_;
    private final Map responseMap_;
    private final ClientTracker clientTracker_;
    private final CallHandler callHandler_;
    private boolean isActive_;
    private HubConnection connection_;
    private Metadata metadata_;
    private Subscriptions subscriptions_;
    private int autoSec_;
    private Timer regTimer_;
    private int iCall_;
    private final Logger logger_ =
        Logger.getLogger( HubConnector.class.getName() );

    private static final String SHUTDOWN_MTYPE = "samp.hub.event.shutdown";
    private static final String DISCONNECT_MTYPE = "samp.hub.disconnect";
    private static final String PING_MTYPE = "samp.app.ping";

    /**
     * Constructs a HubConnector based on a given profile instance.
     * A default client set implementation is used.
     *
     * @param  profile  profile implementation
     */
    public HubConnector( ClientProfile profile ) {
        this( profile, new TrackedClientSet() );
    }

    /**
     * Constructs a HubConnector based on a given profile instance
     * using a custom client set implementation.
     *
     * @param  profile  profile implementation
     * @param  clientSet  object to keep track of registered clients
     */
    protected HubConnector( ClientProfile profile,
                            TrackedClientSet clientSet ) {
        profile_ = profile;
        clientSet_ = clientSet;
        isActive_ = true;

        // Set up data structures.
        messageHandlerList_ = new ArrayList();
        responseHandlerList_ = new ArrayList();
        callable_ = new ConnectorCallableClient();
        responseMap_ = Collections.synchronizedMap( new HashMap() );

        // Listen out for events describing changes to registered clients.
        clientTracker_ = new ClientTracker( clientSet_ );
        addMessageHandler( clientTracker_ );

        // Listen out for hub shutdown events.
        addMessageHandler( new AbstractMessageHandler( SHUTDOWN_MTYPE ) {
            public Map processCall( HubConnection connection,
                                    String senderId, Message message ) {
                String mtype = message.getMType();
                assert SHUTDOWN_MTYPE.equals( mtype );
                checkHubMessage( connection, senderId, mtype );
                disconnect();
                return null;
            }
        } );

        // Listen out for forcible disconnection events.
        addMessageHandler( new AbstractMessageHandler( DISCONNECT_MTYPE ) {
            public Map processCall( HubConnection connection,
                                    String senderId, Message message ) {
                String mtype = message.getMType();
                assert DISCONNECT_MTYPE.equals( mtype );
                if ( senderId.equals( connection.getRegInfo().getHubId() ) ) {
                    Object reason = message.getParam( "reason" );
                    logger_.warning( "Forcible disconnect from hub"
                                   + ( reason == null ? " [no reason given]"
                                                      : " (" + reason + ")" ) );
                    disconnect();
                    return null;
                }
                else {
                    throw new IllegalArgumentException( "Ignoring " + mtype
                                                      + " message from non-hub"
                                                      + " client " + senderId );
                }
            }
        } );

        // Implement samp.app.ping MType.
        addMessageHandler( new AbstractMessageHandler( PING_MTYPE ) {
            public Map processCall( HubConnection connection,
                                    String senderId, Message message )
                    throws InterruptedException {
                String waitMillis = (String) message.getParam( "waitMillis" );
                if ( waitMillis != null ) {
                    Object lock = new Object();
                    synchronized ( lock ) {
                        lock.wait( SampUtils.decodeInt( waitMillis ) );
                    }
                }
                return null;
            }
        } );

        // Listen out for responses to calls for which we are providing
        // faked synchronous call behaviour.
        addResponseHandler( new ResponseHandler() {
            public boolean ownsTag( String msgTag ) {
                return responseMap_.containsKey( msgTag );
            }
            public void receiveResponse( HubConnection connection,
                                         String responderId, String msgTag,
                                         Response response ) {
                synchronized ( responseMap_ ) {
                    if ( responseMap_.containsKey( msgTag ) &&
                         responseMap_.get( msgTag ) == null ) {
                        responseMap_.put( msgTag, response );
                        responseMap_.notifyAll();
                    }
                }
            }
        } );

        // Listen out for responses to calls for which we have agreed to
        // pass results to user-supplied ResultHandler objects.
        callHandler_ = new CallHandler();
        addResponseHandler( callHandler_ );
    }

    /**
     * Sets the interval at which this connector attempts to connect to a
     * hub if no connection currently exists.
     * Otherwise, a connection will be attempted whenever
     * {@link #getConnection} is called.
     *
     * @param  autoSec  number of seconds between attempts;
     *                  &lt;=0 means no automatic connections are attempted
     */
    public synchronized void setAutoconnect( int autoSec ) {
        autoSec_ = autoSec;

        // Cancel and remove any existing auto-connection timer.
        if ( regTimer_ != null ) {
            regTimer_.cancel();
            regTimer_ = null;
        }

        // If required, install a new one.
        if ( autoSec > 0 ) {
            TimerTask regTask = new TimerTask() {
                public void run() {
                    if ( ! isConnected() ) {
                        try {
                            HubConnection conn = getConnection();
                            if ( conn == null ) {
                                logger_.config( "SAMP autoconnection attempt "
                                              + "failed" );
                            }
                            else {
                                logger_.info( "SAMP autoconnection attempt "
                                            + "succeeded" );
                            }
                        }
                        catch ( SampException e ) {
                            logger_.config( "SAMP Autoconnection attempt "
                                          + " failed: " + e );
                        }
                    }
                }
            };
            regTimer_ = new Timer( true );
            regTimer_.schedule( regTask, 0, autoSec_ * 1000 );
        }
    }

    /**
     * Declares the metadata for this client.
     * This declaration affects the current connection and any future ones.
     *
     * @param   meta  {@link org.astrogrid.samp.Metadata}-like map
     */
    public void declareMetadata( Map meta ) {
        Metadata md = Metadata.asMetadata( meta );
        md.check();
        metadata_ = md;
        if ( isConnected() ) {
            try {
                connection_.declareMetadata( md );
            }
            catch ( SampException e ) {
                logger_.log( Level.WARNING,
                             "SAMP metadata declaration failed", e );
            }
        }
    }

    /**
     * Returns this client's own metadata.
     *
     * @return   metadata
     */
    public Metadata getMetadata() {
        return metadata_;
    }

    /**
     * Declares the MType subscriptions for this client.
     * This declaration affects the current connection and any future ones.
     *
     * <p>Note that this call must be made, with a subscription list
     * which includes the various hub administrative messages, in order
     * for this connector to act on those messages (for instance to
     * update its client map and so on).  For this reason, it is usual
     * to call it with the <code>subs</code> argument given by
     * the result of calling {@link #computeSubscriptions}.
     *
     * @param  subscriptions  {@link org.astrogrid.samp.Subscriptions}-like map
     */
    public void declareSubscriptions( Map subscriptions ) {
        Subscriptions subs = Subscriptions.asSubscriptions( subscriptions );
        subs.check();
        subscriptions_ = subs;
        if ( isConnected() ) {
            try {
                connection_.declareSubscriptions( subs );
            }
            catch ( SampException e ) {
                logger_.log( Level.WARNING, "Subscriptions declaration failed",
                             e );
            }
        }
    }

    /**
     * Returns this client's own subscriptions.
     *
     * @return  subscriptions
     */
    public Subscriptions getSubscriptions() {
        return subscriptions_;
    }

    /**
     * Works out the subscriptions map for this connector.
     * This is based on the subscriptions declared by for any
     * {@link MessageHandler}s installed in this connector as well as
     * any MTypes which this connector implements internally.
     * The result of this method is usually a suitable value to pass
     * to {@link #declareSubscriptions}.  However you might wish to
     * remove some entries from the result if there are temporarily
     * unsubscribed services.
     *
     * @return  subscription list for MTypes apparently implemented by this
     *          connector
     */
    public Subscriptions computeSubscriptions() {
        Map subs = new HashMap();
        List mhlist = new ArrayList( messageHandlerList_ );
        Collections.reverse( mhlist );
        for ( Iterator it = mhlist.iterator(); it.hasNext(); ) {
            MessageHandler handler = (MessageHandler) it.next();
            subs.putAll( handler.getSubscriptions() );
        }
        return Subscriptions.asSubscriptions( subs );
    }

    /**
     * Adds a MessageHandler to this connector, which allows it to respond
     * to incoming messages.
     * Note that this does not in itself update the list of subscriptions
     * for this connector; you may want to follow it with a call to
     * <pre>
     *    declareSubscriptions(computeSubscriptions());
     * </pre>
     *
     * @param  handler  handler to add
     */
    public void addMessageHandler( MessageHandler handler ) {
        messageHandlerList_.add( handler );
    }

    /**
     * Removes a previously-added MessageHandler to this connector.
     * Note that this does not in itself update the list of subscriptions
     * for this connector; you may want to follow it with a call to
     * <pre>
     *    declareSubscriptions(computeSubscriptions());
     * </pre>
     *
     * @param  handler  handler to remove
     */
    public void removeMessageHandler( MessageHandler handler ) {
        messageHandlerList_.remove( handler );
    }

    /**
     * Adds a ResponseHandler to this connector, which allows it to receive
     * replies from messages sent asynchronously.
     *
     * <p>Note however that this class's {@link #callAndWait callAndWait} method
     * can provide a synchronous facade for fully asynchronous messaging,
     * which in many cases will be more convenient than installing your
     * own response handlers to deal with asynchronous replies.
     *
     * @param  handler  handler to add
     */
    public void addResponseHandler( ResponseHandler handler ) {
        responseHandlerList_.add( handler );
    }

    /**
     * Removes a ResponseHandler from this connector.
     *
     * @param  handler  handler to remove
     */
    public void removeResponseHandler( ResponseHandler handler ) {
        responseHandlerList_.remove( handler );
    }

    /**
     * Sets whether this connector is active or not.
     * If set false, any existing connection will be terminated (the client
     * will unregister) and autoconnection attempts will be suspended.
     * If set true, if there is no existing connection an attempt will be
     * made to register, and autoconnection attempts will begin if applicable.
     *
     * @param  active  whether this connector should be active
     * @see  #setAutoconnect
     */
    public void setActive( boolean active ) {
        isActive_ = active;
        if ( active ) {
            if ( connection_ == null ) {
                try {
                    getConnection();
                }
                catch ( SampException e ) {
                    logger_.log( Level.WARNING,
                                 "Hub connection attempt failed", e );
                }
            }
        }
        else { 
            HubConnection connection = connection_;
            if ( connection != null ) {
                disconnect();
                try {
                    connection.unregister();
                }
                catch ( SampException e ) {
                    logger_.log( Level.INFO, "Unregister attempt failed", e );
                }
            }
        }
    }

    /**
     * Sends a message synchronously to a client, waiting for the response.
     * If more seconds elapse than the value of the <code>timeout</code>
     * parameter, an exception will result.
     *
     * <p>The semantics of this call are, as far as the caller is concerned,
     * identical to that of the similarly named {@link HubConnection} method.
     * However, in this case the client communicates with the hub
     * asynchronously and internally simulates the synchrony for the caller,
     * rather than letting the hub do that.
     * This is more robust and almost certainly a better idea.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  timeout  timeout in seconds, or &lt;0 for no timeout
     * @return  response
     */
    public Response callAndWait( String recipientId, Map msg, int timeout )
            throws SampException {
        long finish = timeout > 0
                    ? System.currentTimeMillis() + timeout * 1000
                    : Long.MAX_VALUE;  // 3e8 years
        HubConnection connection = getConnection();
        String msgTag = createTag( this );
        responseMap_.put( msgTag, null );
        connection.call( recipientId, msgTag, msg );
        synchronized ( responseMap_ ) {
            while ( responseMap_.containsKey( msgTag ) &&
                    responseMap_.get( msgTag ) == null &&
                    System.currentTimeMillis() < finish ) {
                long millis = finish - System.currentTimeMillis();
                if ( millis > 0 ) {
                    try {
                        responseMap_.wait( millis );
                    }
                    catch ( InterruptedException e ) {
                        throw new SampException( "Wait interrupted", e );
                    }
                }
            }
            if ( responseMap_.containsKey( msgTag ) ) {
                Response response = (Response) responseMap_.remove( msgTag );
                if ( response != null ) {
                    return response;
                }
                else {
                    assert System.currentTimeMillis() >= finish;
                    throw new SampException( "Synchronous call timeout" );
                }
            }
            else {
                if ( connection != connection_ ) {
                    throw new SampException( "Hub connection lost" );
                }
                else {
                    throw new AssertionError();
                }
            }
        }
    }

    /**
     * Sends a message asynchronously to a single client, making a callback 
     * on a supplied ResultHandler object when the result arrives.
     * The {@link org.astrogrid.samp.client.ResultHandler#done} method will
     * be called after the result has arrived or the timeout elapses,
     * whichever happens first.
     *
     * <p>This convenience method allows the user to make an asynchronous
     * call without having to worry registering message handlers and
     * matching message tags.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  resultHandler  object called back when response arrives or 
     *                        timeout is exceeded
     * @param  timeout  timeout in seconds, or &lt;0 for no timeout
     */
    public void call( String recipientId, Map msg, ResultHandler resultHandler,
                      int timeout ) throws SampException {
        HubConnection connection = getConnection();
        String tag = createTag( this );
        callHandler_.registerHandler( tag, resultHandler, timeout );
        try {
            connection.call( recipientId, tag, msg );
            callHandler_.setRecipients( tag, new String[] { recipientId, } );
        }
        catch ( SampException e ) {
            callHandler_.unregisterHandler( tag );
            throw e;
        }
    }

    /**
     * Sends a message asynchronously to all subscribed clients, 
     * making callbacks on a supplied ResultHandler object when the
     * results arrive.
     * The {@link org.astrogrid.samp.client.ResultHandler#done} method will
     * be called after all the results have arrived or the timeout elapses,
     * whichever happens first.
     *
     * <p>This convenience method allows the user to make an asynchronous
     * call without having to worry registering message handlers and
     * matching message tags.
     *
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  resultHandler  object called back when response arrives or 
     *                        timeout is exceeded
     * @param  timeout  timeout in seconds, or &lt;0 for no timeout
     */
    public void callAll( Map msg, ResultHandler resultHandler, int timeout )
            throws SampException {
        HubConnection connection = getConnection();
        String tag = createTag( this );
        callHandler_.registerHandler( tag, resultHandler, timeout );
        try {
            Map callMap = connection.callAll( tag, msg );
            callHandler_.setRecipients( tag,
                                        (String[])
                                        callMap.keySet()
                                               .toArray( new String[ 0 ] ) );
        }
        catch ( SampException e ) {
            callHandler_.unregisterHandler( tag );
            throw e;
        }
    }

    /**
     * Indicates whether this connector is currently registered with a
     * running hub.
     * If true, the result of {@link #getConnection} will be non-null.
     *
     * @return   true  if currently connected to a hub
     */
    public boolean isConnected() {
        return connection_ != null;
    }

    /**
     * If necessary attempts to acquire, and returns, a connection to a
     * running hub.
     * If there is an existing connection representing a registration
     * with a hub, it is returned.  If not, and this connector is active,
     * an attempt is made to connect and register, followed by a call to
     * {@link #configureConnection configureConnection}, is made.
     *
     * <p>Note that if {@link #setActive setActive(false)} has been called,
     * null will be returned.
     *
     * @return  hub connection representing configured registration with a hub
     *          if a hub is running; if not, null
     * @throws  SampException  in the case of some unexpected error
     */
    public HubConnection getConnection() throws SampException {
        HubConnection connection = connection_;
        if ( connection == null && isActive_ ) {
            connection = createConnection();
            if ( connection != null ) {
                connection_ = connection;
                configureConnection( connection );
                clientTracker_.initialise( connection );
            }
        }
        return connection;
    }

    /**
     * Configures a connection with a hub in accordance with the state of
     * this object.
     * The hub is made aware of how to perform callbacks on the registered
     * client, and any current metadata and subscriptions are declared.
     *
     * @param  connection  connection representing registration with a hub
     */
    public void configureConnection( HubConnection connection )
            throws SampException {
        if ( metadata_ != null ) {
            connection.declareMetadata( metadata_ );
        }
        if ( callable_ != null ) {
            connection.setCallable( callable_ );
            callable_.setConnection( connection );
            if ( subscriptions_ != null ) {
                connection.declareSubscriptions( subscriptions_ );
            }
        }
    }

    /**
     * Returns a map which keeps track of other clients currently registered
     * with the hub to which this object is connected, including their
     * currently declared metadata and subscriptions.
     * Map keys are public IDs and values are
     * {@link org.astrogrid.samp.Client}s.
     *
     * <p>This map is {@link java.util.Collections#synchronizedMap synchronized}
     * which means that to iterate over any of its views 
     * you must synchronize on it.
     * When the map or any of its contents changes, it will receive a
     * {@link java.lang.Object#notifyAll}.
     *
     * @return   id->Client map
     */
    public Map getClientMap() {
        return getClientSet().getClientMap();
    }

    /**
     * Returns the tracked client set implementation which is used to keep
     * track of the currently registered clients.
     *
     * @return  client set implementation
     */
    protected TrackedClientSet getClientSet() {
        return clientSet_;
    }

    /**
     * Invoked by this class to create a hub connection.
     * The default implementation just calls <code>profile.register()</code>.
     *
     * @return   new hub connection
     */
    protected HubConnection createConnection() throws SampException {
        return profile_.register();
    }

    /**
     * Unregisters from the currently connected hub, if any.
     * Performs any associated required cleanup.
     */
    protected void disconnect() {
        connection_ = null;
        clientTracker_.clear();
        synchronized ( responseMap_ ) {
            responseMap_.clear();
            responseMap_.notifyAll();
        }
    }

    /**
     * Performs sanity checking on a message which is normally expected to
     * be sent only by the hub client itself.
     *
     * @param  connection  connection to the hub
     * @param  senderId  public client id of sender
     * @param  mtype    MType of sent message
     */
    private void checkHubMessage( HubConnection connection, String senderId,
                                  String mtype ) {
        if ( ! senderId.equals( connection.getRegInfo().getHubId() ) ) {
            logger_.warning( "Hub admin message " + mtype + " received from "
                           + "non-hub client.  Acting on it anyhow" );
        }
    }

    /**
     * Generates a new <code>msgTag</code> for use with this connector.
     * It is guaranteed to return a different value on each invocation.
     * It is advisable to use this method whenever a message tag is required
     * to prevent clashes.
     *
     * @param  owner  object to identify caller
     *                (not really necessary - may be null)
     * @return  unique tag for this connector
     */
    public synchronized String createTag( Object owner ) {
        return String.valueOf( owner ) + ":" + ++iCall_;
    }

    /**
     * CallableClient implementation used by this class.
     */
    private class ConnectorCallableClient implements CallableClient {

        private HubConnection conn_;

        /**
         * Sets the currently active hub connection.
         *
         * @param  connection  connection
         */
        private void setConnection( HubConnection connection ) {
            conn_ = connection;
        }

        public void receiveNotification( String senderId, Message message ) {

            // Offer the notification to each registered MessageHandler in turn.
            // It may in principle get processed by more than one.
            // This is almost certainly harmless.
            for ( Iterator it = messageHandlerList_.iterator();
                  it.hasNext(); ) {
                MessageHandler handler = (MessageHandler) it.next();
                Subscriptions subs =
                    Subscriptions.asSubscriptions( handler.getSubscriptions() );
                if ( subs.isSubscribed( message.getMType() ) ) {
                    try {
                        handler.receiveNotification( conn_, senderId, message );
                    }
                    catch ( Exception e ) {
                        logger_.log( Level.WARNING, "Notify handler failed",
                                     e );
                    }
                }
            }
        }

        public void receiveCall( String senderId, String msgId,
                                 Message message ) {

            // Offer the call to each registered MessageHandler in turn.
            // Since only one should be allowed to respond to it, only
            // the first one which bites is allowed to process it.
            String mtype = message.getMType();
            ErrInfo errInfo = null;
            for ( Iterator it = messageHandlerList_.iterator();
                  it.hasNext(); ) {
                MessageHandler handler = (MessageHandler) it.next();
                Subscriptions subs =
                    Subscriptions.asSubscriptions( handler.getSubscriptions() );
                if ( subs.isSubscribed( mtype ) ) {
                    try {
                        handler.receiveCall( conn_, senderId, msgId, message );
                        return;
                    }
                    catch ( Exception e ) {
                        errInfo = new ErrInfo( e );
                        logger_.log( Level.WARNING,
                                     "Call handler failed: " + mtype );
                    }
                }
            }
            if ( errInfo == null ) {
                logger_.warning( "No handler for subscribed MType " + mtype );
                errInfo = new ErrInfo( "No handler found" );
                errInfo.setUsertxt( "No handler was found for the supplied"
                                  + " MType. "
                                  + "Looks like a programming error "
                                  + "at the  recipient end.  Sorry." );
            }
            Response response = Response.createErrorResponse( errInfo );
            response.check();
            try {
                conn_.reply( msgId, response );
            }
            catch ( SampException e ) {
                logger_.warning( "Failed to reply to " + msgId );
            }
        }

        public void receiveResponse( String responderId, String msgTag,
                                     Response response ) {

            // Offer the response to each registered ResponseHandler in turn.
            // It shouldn't be processed by more than one, but if it is,
            // warn about it.
            int handleCount = 0;
            for ( Iterator it = responseHandlerList_.iterator();
                  it.hasNext(); ) {
                ResponseHandler handler = (ResponseHandler) it.next();
                if ( handler.ownsTag( msgTag ) ) {
                    handleCount++;
                    try {
                        handler.receiveResponse( conn_, responderId, msgTag,
                                                 response );
                    }
                    catch ( Exception e ) {
                        logger_.log( Level.WARNING, "Response handler failed",
                                     e );
                    }
                }
            }
            if ( handleCount == 0 ) {
                logger_.warning( "No handler for message "
                               + msgTag + " response" );
            }
            else if ( handleCount > 1 ) {
                logger_.warning( "Multiple (" + handleCount + ")"
                               + " handlers handled message "
                               + msgTag + " respose" );
            }
        }
    }

    /**
     * ResponseHandler which looks after responses made by calls using the 
     * call() and callAll() convenience methods.
     */
    private class CallHandler implements ResponseHandler {
        private final SortedMap tagMap_;
        private final Thread timeouter_;

        /**
         * Constructor.
         */
        CallHandler() {

            // Set up a structure to contain tag->CallItem entries for
            // responses we are expecting.  They are arranged in order of
            // which is going to time out soonest.
            tagMap_ = new TreeMap();

            // Set up a thread to wake up when the next timeout has 
            // (or at least might have) happened.
            timeouter_ = new Thread( "ResultHandler timeout watcher" ) {
                public void run() {
                    watchTimeouts();
                }
            };
            timeouter_.setDaemon( true );
            timeouter_.start();
        }

        /**
         * Runs in a daemon thread to watch out for timeouts that might
         * have occurred.
         */
        private void watchTimeouts() {
            while ( true ) {
                synchronized ( tagMap_ ) {

                    // Wait until the next scheduled timeout is expected.
                    long nextFinish =
                        tagMap_.isEmpty() ? Long.MAX_VALUE
                                          : ((CallItem)
                                             tagMap_.get( tagMap_.firstKey() ))
                                            .finish_;
                    final long delay = nextFinish - System.currentTimeMillis();
                    if ( delay > 0 ) {
                        try {
                            tagMap_.wait( delay );
                        }
                        catch ( InterruptedException e ) {
                        }
                    }

                    // Then process any timeouts that are pending.
                    long now = System.currentTimeMillis();
                    for ( Iterator it = tagMap_.entrySet().iterator();
                          it.hasNext(); ) {
                        Map.Entry entry = (Map.Entry) it.next();
                        CallItem item = (CallItem) entry.getValue();
                        if ( now >= item.finish_ ) {
                            item.handler_.done();
                            it.remove();
                        }
                    }
                }
            }
        }

        /**
         * Stores a ResultHandler object which will take delivery of the
         * responses tagged with a given tag.
         *
         * @param  tag  message tag identifying send/response
         * @param  handler  callback object
         * @param  timeout  milliseconds before forcing completion
         */
        public void registerHandler( String tag, ResultHandler handler,
                                     int timeout ) {
            long finish = timeout > 0
                        ? System.currentTimeMillis() + timeout * 1000
                        : Long.MAX_VALUE;  // 3e8 years
            CallItem item = new CallItem( handler, finish );
            if ( ! item.isDone() ) {
                synchronized ( tagMap_ ) {
                    tagMap_.put( tag, item );
                    tagMap_.notifyAll();
                }
            }
            else {
                handler.done();
            }
        }

        /**
         * Set the recipients from which we are expecting responses.
         * Once all are in, the handler can be disposed of.
         *
         * @param  tag  message tag identifying send/response
         * @param  recipients   clients expected to reply
         */
        public void setRecipients( String tag, String[] recipients ) {
            CallItem item;
            synchronized ( tagMap_ ) {
                item = (CallItem) tagMap_.get( tag );
            }
            item.setRecipients( recipients );
            retireIfDone( tag, item );
        }

        /**
         * Unregister a handler for which no responses are expected.
         *
         * @param  tag  message tag identifying send/response
         */
        public void unregisterHandler( String tag ) {
            synchronized ( tagMap_ ) {
                tagMap_.remove( tag );
            }
        }

        public boolean ownsTag( String tag ) {
            synchronized ( tagMap_ ) {
                return tagMap_.containsKey( tag );
            }
        }

        public void receiveResponse( HubConnection connection,
                                     String responderId, String msgTag,
                                     Response response ) {
            final CallItem item;
            synchronized ( tagMap_ ) {
                item = (CallItem) tagMap_.get( msgTag );
            }
            if ( item != null ) {
                item.addResponse( responderId, response );
                retireIfDone( msgTag, item );
            }
        }

        /**
         * Called when a tag/handler entry might be ready to finish with.
         */
        private void retireIfDone( String tag, CallItem item ) {
            if ( item.isDone() ) {
                synchronized ( tagMap_ ) {
                    item.handler_.done();
                    tagMap_.remove( tag );
                }
            }
        }
    }

    /**
     * Stores state about a particular set of responses expected by the
     * CallHandler class.
     */
    private class CallItem implements Comparable {
        final ResultHandler handler_;
        final long finish_;
        Map responseMap_;  // responderId -> Response
        Map recipientMap_; // responderId -> Client
 
        /**
         * Constructor.
         *
         * @param  handler  callback object
         * @param  finish   epoch at which timeout should be called
         */
        CallItem( ResultHandler handler, long finish ) {
            handler_ = handler;
            finish_ = finish;
        }

        /**
         * Sets the recipient Ids for which responses are expected.
         *
         * @param   recipientIds  recipient client ids
         */
        public synchronized void setRecipients( String[] recipientIds ) {
            recipientMap_ = new HashMap();

            // Store client objects for each recipient ID.  Note however 
            // because of various synchrony issues we can't guarantee that
            // all these client objects can be determined - some may be null.
            // Store the ids as keys in any case.
            Map clientMap = getClientMap();
            for ( int ir = 0; ir < recipientIds.length; ir++ ) {
                String id = recipientIds[ ir ];
                Client client = (Client) clientMap.get( id );
                recipientMap_.put( id, client );
            }

            // If we have pending responses (couldn't be processed earlier
            // because no recipients), take care of them now.
            if ( responseMap_ != null ) {
                for ( Iterator it = responseMap_.entrySet().iterator();
                      it.hasNext(); ) {
                    Map.Entry entry = (Map.Entry) it.next();
                    String responderId = (String) entry.getKey();
                    Response response = (Response) entry.getValue();
                    processResponse( responderId, response );
                }
                responseMap_ = null;
            }
        }

        /**
         * Take delivery of a response object.
         *
         * @param  responderId  client ID of responder
         * @param  response   response object
         */
        public synchronized void addResponse( String responderId,
                                              Response response ) {

            // If we know the recipients, deal with it now.
            if ( recipientMap_ != null ) {
                processResponse( responderId, response );
            }

            // Otherwise, defer until we do know the recipients.
            else {
                if ( responseMap_ == null ) {
                    responseMap_ = new HashMap();
                }
                responseMap_.put( responderId, response );
            }
        }

        /**
         * Process a response when we have both the list of recipients
         * and the response itself.
         *
         * @param   responderId  client ID of responder
         * @param   response   response object
         */
        private synchronized void processResponse( final String responderId,
                                                   Response response ) {
            if ( recipientMap_.containsKey( responderId ) ) {

                // Get a client object.  We have to try belt and braces.
                Client client = (Client) recipientMap_.get( responderId );
                if ( client == null ) {
                    client = (Client) getClientMap().get( responderId );
                }
                if ( client == null ) {
                    client = new Client() {
                        public String getId() {
                            return responderId;
                        }
                        public Metadata getMetadata() {
                            return null;
                        }
                        public Subscriptions getSubscriptions() {
                            return null;
                        }
                    };
                }

                // Make the callback to the supplied handler.
                handler_.result( client, response );

                // Note that we've done this one.
                recipientMap_.remove( responderId );
            }
        }

        /**
         * Indicate whether this call item has received all the responses it's
         * going to.
         *
         * @return  iff no further activity is expected
         */
        public synchronized boolean isDone() {
            return recipientMap_ != null
                && recipientMap_.isEmpty();
        }

        /**
         * Compares on timeout epochs.  
         * Implementation is <em>consistent with equals</em>,
         * which means it's OK to use them in a SortedMap.
         */
        public int compareTo( Object o ) {
            CallItem other = (CallItem) o;
            if ( this.finish_ < other.finish_ ) {
                return -1;
            }
            else if ( this.finish_ > other.finish_ ) {
                return +1;
            }
            else {
                return System.identityHashCode( this )
                     - System.identityHashCode( other );
            }
        }
    }
}
