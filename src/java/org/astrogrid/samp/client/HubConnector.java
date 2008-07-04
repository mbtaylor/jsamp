package org.astrogrid.samp.client;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
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
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public class HubConnector {

    private final List messageHandlerList_;
    private final List responseHandlerList_;
    private final ConnectorCallableClient callable_;
    private final Map responseMap_;
    private RegisterAction regAction_;
    private ClientTracker clientTracker_;
    private boolean isActive_;
    private HubConnection connection_;
    private Metadata metadata_;
    private Subscriptions subscriptions_;
    private CallableClientServer clientServer_;
    private int autoSec_;
    private Timer regTimer_;
    private int iCall_;
    private final Logger logger_ =
        Logger.getLogger( HubConnector.class.getName() );

    private static final String SHUTDOWN_MTYPE = "samp.hub.event.shutdown";
    private static final String PING_MTYPE = "samp.app.ping";

    public HubConnector() {
        messageHandlerList_ = new ArrayList();
        responseHandlerList_ = new ArrayList();
        callable_ = new ConnectorCallableClient();
        responseMap_ = Collections.synchronizedMap( new HashMap() );
        clientTracker_ = new ClientTracker();
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
        addMessageHandler( new AbstractMessageHandler( PING_MTYPE ) {
            public Map processCall( HubConnection connection,
                                    String senderId, Message message ) {
                return null;
            }
        } );
        addMessageHandler( clientTracker_ );
        addResponseHandler( new ResponseHandler() {
            public boolean ownsTag( String msgTag ) {
                return responseMap_.containsKey( msgTag );
            }
            public void receiveResponse( HubConnection connection,
                                         String responderId, String msgTag,
                                         Response response ) {
                if ( responseMap_.containsKey( msgTag ) &&
                     responseMap_.get( msgTag ) == null ) {
                    responseMap_.put( msgTag, response );
                    responseMap_.notifyAll();
                }
            }
        } );
    }

    public HubConnector( Map metadata ) {
        this();
        try {
            declareMetadata( metadata );
        }
        catch ( SampException e ) {
            throw new AssertionError( e );
        }
    }

    public void setAutoconnect( int autoSec ) {
        autoSec_ = autoSec;
        if ( regTimer_ != null ) {
            regTimer_.cancel();
        }
        if ( autoSec > 0 ) {
            TimerTask regTask = new TimerTask() {
                public void run() {
                    try {
                        getConnection();
                    }
                    catch ( SampException e ) {
                    }
                }
            };
            regTimer_ = new Timer( true );
            regTimer_.schedule( regTask, 0, autoSec_ * 1000 );
        }
    }

    public void declareMetadata( Map metadata ) throws SampException {
        Metadata md = Metadata.asMetadata( metadata );
        md.check();
        metadata_ = md;
        if ( isConnected() ) {
            connection_.declareMetadata( md );
        }
    }

    public Metadata getMetadata() {
        return metadata_;
    }

    public void declareSubscriptions( Map subscriptions )
            throws SampException {
        Subscriptions subs = Subscriptions.asSubscriptions( subscriptions );
        subs.check();
        subscriptions_ = subs;
        if ( isConnected() ) {
            connection_.declareSubscriptions( subs );
        }
    }

    public Subscriptions getSubscriptions() {
        return subscriptions_;
    }

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

    public void addMessageHandler( MessageHandler handler ) {
        messageHandlerList_.add( handler );
    }

    public void removeMessageHandler( MessageHandler handler ) {
        messageHandlerList_.remove( handler );
    }

    public void addResponseHandler( ResponseHandler handler ) {
        responseHandlerList_.add( handler );
    }

    public void removeResponseHandler( ResponseHandler handler ) {
        responseHandlerList_.remove( handler );
    }

    public void setActive( boolean active ) {
        isActive_ = active;
        if ( regAction_ != null ) {
            regAction_.updateState();
        }
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
                }
            }
        }
    }

    public Response callAndWait( String recipientId, Map msg, int timeout )
            throws SampException {
        long finish = timeout > 0
                    ? System.currentTimeMillis() + timeout * 1000
                    : Long.MAX_VALUE;  // 3e8 years
        HubConnection connection = getConnection();
        String msgTag = generateTag();
        connection.call( recipientId, msgTag, msg );
        responseMap_.put( msgTag, null );
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

    public boolean isConnected() {
        return connection_ != null;
    }

    public HubConnection getConnection() throws SampException {
        HubConnection connection = connection_;
        if ( connection == null && isActive_ ) {
            connection = createConnection();
            if ( connection != null ) {
                configureConnection( connection );
                connection_ = connection;
                if ( regAction_ != null ) {
                    regAction_.updateState();
                }
                clientTracker_.initialise( connection );
            }
        }
        return connection;
    }

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

    public Action getRegisterAction() {
        if ( regAction_ == null ) {
            regAction_ = new RegisterAction();
        }
        return regAction_;
    }

    /**
     * ListModel elements are {@link org.astrogrid.samp.Client}s.
     */
    public ListModel getClientListModel() {
        return clientTracker_.getClientListModel();
    }

    /**
     * Map keys are public IDs and values are 
     * {@link org.astrogrid.samp.Client}s.
     */
    public Map getClientMap() {
        return clientTracker_.getClientMap();
    }

    private void disconnect() {
        connection_ = null;
        if ( regAction_ != null ) {
            regAction_.updateState();
        }
        clientTracker_.clear();
        synchronized ( responseMap_ ) {
            responseMap_.clear();
            responseMap_.notifyAll();
        }
    }

    private void checkHubMessage( HubConnection connection, String senderId, 
                                 String mtype ) {
        if ( ! senderId.equals( connection.getRegInfo().getHubId() ) ) {
            logger_.warning( "Hub admin message " + mtype + " received from "
                           + "non-hub client.  Acting on it anyhow" );
        }
    }

    private String generateTag() {
        return this.toString() + ":" + ++iCall_;
    }

    public static HubConnection createConnection() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo = LockInfo.readLockFile();
        }
        catch ( IOException e ) {
            throw new SampException( "Error reading lockfile", e );
        }
        if ( lockInfo == null ) {
            return null;
        }
        else {
            return new XmlRpcHubConnection( lockInfo.getXmlrpcUrl(),
                                            lockInfo.getSecret() );
        }
    }

    private class ConnectorCallableClient implements CallableClient {

        private HubConnection conn_;

        private void setConnection( HubConnection connection ) {
            conn_ = connection;
        }

        public void receiveNotification( String senderId, Message message )
                throws SampException {
            for ( Iterator it = messageHandlerList_.iterator();
                  it.hasNext(); ) {
                MessageHandler handler = (MessageHandler) it.next();
                Subscriptions subs =
                    Subscriptions.asSubscriptions( handler.getSubscriptions() );
                if ( subs.isSubscribed( message.getMType() ) ) {
                    handler.receiveNotification( conn_, senderId, message );
                }
            }
        }

        public void receiveCall( String senderId, String msgId,
                                 Message message )
                throws SampException {
            for ( Iterator it = messageHandlerList_.iterator();
                  it.hasNext(); ) {
                MessageHandler handler = (MessageHandler) it.next();
                Subscriptions subs =
                    Subscriptions.asSubscriptions( handler.getSubscriptions() );
                if ( subs.isSubscribed( message.getMType() ) ) {
                    handler.receiveCall( conn_, senderId, msgId, message );
                    return;
                }
            }
        }

        public void receiveResponse( String responderId, String msgTag,
                                     Response response )
                throws SampException {
            int handleCount = 0;
            for ( Iterator it = responseHandlerList_.iterator();
                  it.hasNext(); ) {
                ResponseHandler handler = (ResponseHandler) it.next();
                if ( handler.ownsTag( msgTag ) ) {
                    handleCount++;
                    handler.receiveResponse( conn_, responderId, msgTag,
                                             response );
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

    private class RegisterAction extends AbstractAction {

        public RegisterAction() {
            doUpdateState();
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

        private void updateState() {
            if ( SwingUtilities.isEventDispatchThread() ) {
                doUpdateState();
            }
            else {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        doUpdateState();
                    }
                } );
            }
        }

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
