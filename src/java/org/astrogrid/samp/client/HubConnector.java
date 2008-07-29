package org.astrogrid.samp.client;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
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
 * <li>Provides hooks for GUI components
 * </ul>
 *
 * <p>This object provides a {@link #getConnection} method which provides
 * the currently active {@link HubConnection} object if one exists or can be 
 * acquired.  The <code>HubConnection</code> can be used for direct calls
 * on the running hub, but in some cases similar methods with additional
 * functionality exist in this class:
 * <dl>
 * <dt>{@link #declareMetadata}
 * <dt>{@link #declareSubscriptions}
 * <dd>These methods not only make the relevant declarations to the 
 *     existing hub connection, if one exists, but will retain the 
 *     metadata and subscriptions information and declare them to 
 *     other connections if the hub connection is terminated and 
 *     restarted (with either the same or a different hub)
 *     over the lifetime of this object.
 *     </dd>
 * <dt>{@link #callAndWait}
 * <dd>Provides identical semantics to the similarly named 
 *     <code>HubConnection</code> method, but communicates with the hub 
 *     asynchronously and fakes the synchrony at the client end.
 *     This is more robust and almost certainly a better idea.
 *     </dd>
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
 *   HubConnector conn = new HubConnector()
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
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class HubConnector {

    private final ClientProfile profile_;
    private final List messageHandlerList_;
    private final List responseHandlerList_;
    private final ConnectorCallableClient callable_;
    private final Map responseMap_;
    private final List connectionListenerList_;
    private RegisterAction regAction_;
    private ClientTracker clientTracker_;
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
    private static final String PING_MTYPE = "samp.app.ping";

    /**
     * Constructs a HubConnector based on the SAMP Standard Profile.
     */
    public HubConnector() {
        this( StandardClientProfile.getInstance() );
    }

    /**
     * Constructs a HubConnector based on a given profile instance.
     *
     * @param  profile  profile implementation
     */
    public HubConnector( ClientProfile profile ) {
        profile_ = profile;
        isActive_ = true;

        // Set up data structures.
        messageHandlerList_ = new ArrayList();
        responseHandlerList_ = new ArrayList();
        callable_ = new ConnectorCallableClient();
        responseMap_ = Collections.synchronizedMap( new HashMap() );
        connectionListenerList_ = new ArrayList();

        // Listen out for events describing changes to registered clients.
        clientTracker_ = new ClientTracker();
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

        // Implement samp.app.ping MType.
        addMessageHandler( new AbstractMessageHandler( PING_MTYPE ) {
            public Map processCall( HubConnection connection,
                                    String senderId, Message message ) {
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
    }

    /**
     * Sets the interval at which this connector attempts to connect to a 
     * hub if no connection currently exists.
     * Otherwise, a connection will be attempted whenever 
     * {@link #getConnection} is called.
     * 
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
                    try {
                        HubConnection conn = getConnection();
                        logger_.info( "Autoconnection attempt: "
                                    + ( conn == null ? "failed"
                                                     : "succeeded" ) );
                    }
                    catch ( SampException e ) {
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
                logger_.log( Level.WARNING, "Metadata declaration failed", e );
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
     * <p>Note however that this class's {@link #callAndWait} method
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
        scheduleConnectionChange();
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
        String msgTag = generateTag();
        connection.call( recipientId, msgTag, msg );
        synchronized ( responseMap_ ) {
            responseMap_.put( msgTag, null );
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
     * Adds a listener which will be notified when this connector 
     * registers or unregisters with a hub.
     *
     * @param  listener   listener to add
     */
    public void addConnectionListener( ChangeListener listener ) {
        connectionListenerList_.add( listener );
    }

    /**
     * Removes a listener previously added by {@link #addConnectionListener}.
     *
     * @param   listener  listener to remove
     */
    public void removeConnectionListener( ChangeListener listener ) {
        connectionListenerList_.remove( listener );
    }

    /**
     * Schedule an action to inform listeners that there has been a 
     * change to connection status.  May be called from any thread.
     */
    private void scheduleConnectionChange() {
        final ChangeListener[] listeners = (ChangeListener[])
                                           connectionListenerList_
                                          .toArray( new ChangeListener[ 0 ] );
        if ( listeners.length > 0 ) {
            if ( SwingUtilities.isEventDispatchThread() ) {
                doFireConnectionChange( listeners );
            }
            else {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        doFireConnectionChange( listeners );
                    }
                } );
            }
        }
    }

    /**
     * Inform listeners that there has been a change to connection status.
     * May only be called from the AWT event dispatch thread.
     */
    private void doFireConnectionChange( ChangeListener[] listeners ) {
        assert SwingUtilities.isEventDispatchThread();
        ChangeEvent evt = new ChangeEvent( this );
        for ( int i = 0; i < listeners.length; i++ ) {
            listeners[ i ].stateChanged( evt );
        }
    }

    /**
     * If necessary attempts to acquire, and returns, a connection to a
     * running hub.
     * If there is an existing connection representing a registration 
     * with a hub, it is returned.  If not, and this connector is active, 
     * an attempt is made to connect and register, followed by a call to 
     * {@link #configureConnection}, is made.
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
            connection = profile_.register();
            if ( connection != null ) {
                configureConnection( connection );
                connection_ = connection;
                scheduleConnectionChange();
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
     * Returns an Action which can be used to register/unregister with a hub.
     * Invoking the action toggles the {@link #setActive} state.
     * This action can be used as the model for a {@link javax.swing.JButton}
     * or {@link javax.swing.JMenuItem} which allows users to 
     * register/unregister.
     *
     * @return  register/unregister action
     */
    public Action getRegisterAction() {
        if ( regAction_ == null ) {
            regAction_ = new RegisterAction();
        }
        return regAction_;
    }

    /**
     * Creates a component which indicates whether this connector is currently
     * connected or not, using default icons.
     *
     * @return  connection indicator
     */
    public JComponent createConnectionIndicator() {
        return createConnectionIndicator(
            new ImageIcon( Client.class
                          .getResource( "images/connected-24.gif" ) ),
            new ImageIcon( Client.class
                          .getResource( "images/disconnected-24.gif" ) )
        );
    }

    /**
     * Creates a component which indicates whether this connector is currently
     * connected or not, using supplied icons.
     *
     * @param   onIcon  icon indicating connection
     * @param   offIcon  icon indicating no connection
     * @return  connection indicator
     */
    private JComponent createConnectionIndicator( final Icon onIcon,
                                                  final Icon offIcon ) {
        final JLabel label = new JLabel( isConnected() ? onIcon : offIcon );
        addConnectionListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                label.setIcon( isConnected() ? onIcon : offIcon );
            }
        } );
        return label;
    }

    /**
     * Returns a list model which keeps track of other clients currently
     * registered with the hub to which this object is connected, including
     * their currently declared metadata and subscriptions.
     * This can be used as the model for a {@link javax.swing.JList}.
     *
     * @return   a list model in which the elements are 
     *           {@link org.astrogrid.samp.Client}s
     */
    public ListModel getClientListModel() {
        return clientTracker_.getClientListModel();
    }

    /**
     * Returns a map which keeps track of other clients currently registered
     * with the hub to which this object is connected, including their
     * currently declared metadata and subscriptions.
     * Map keys are public IDs and values are 
     * {@link org.astrogrid.samp.Client}s.
     *
     * @return   id->Client map
     */
    public Map getClientMap() {
        return clientTracker_.getClientMap();
    }

    /**
     * Unregisters from the currently connected hub, if any.
     * Performs any associated required cleanup.
     */
    private void disconnect() {
        connection_ = null;
        scheduleConnectionChange();
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
     * Generates a new msgTag for use by this connector's 
     * {@link #callAndWait} method.
     */
    private String generateTag() {
        return this.toString() + ":" + ++iCall_;
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
                        logger_.log( Level.WARNING, "Call handler failed", e );
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
     * Action implementation which registers/unregisters.
     */
    private class RegisterAction extends AbstractAction {

        /**
         * Constructor.
         */
        public RegisterAction() {
            doUpdateState();
            addConnectionListener( new ChangeListener() {
                public void stateChanged( ChangeEvent evt ) {
                    doUpdateState();
                }
            } );
        }

        public void actionPerformed( ActionEvent evt ) {
            String cmd = evt.getActionCommand();
            if ( "REGISTER".equals( cmd ) ) {
                setActive( true );
                if ( ! isConnected() ) {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
            else if ( "UNREGISTER".equals( cmd ) ) {
                setActive( false );
            }
            else {
                logger_.warning( "Unknown action " + cmd );
            }
        }

        /**
         * Configure this action in accordance with the current
         * connection state.
         */
        private void doUpdateState() {
            if ( isConnected() ) {
                putValue( Action.ACTION_COMMAND_KEY, "UNREGISTER" );
                putValue( Action.NAME, "Unregister from Hub" );
                putValue( Action.SHORT_DESCRIPTION,
                          "Disconnect from SAMP hub" );
            }
            else {
                putValue( Action.ACTION_COMMAND_KEY, "REGISTER" );
                putValue( Action.NAME, "Register with Hub" );
                putValue( Action.SHORT_DESCRIPTION,
                          "Attempt to connect to SAMP hub" );
            }
        }
    }
}
