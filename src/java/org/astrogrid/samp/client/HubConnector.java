package org.astrogrid.samp.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public class HubConnector {

    private final List messageHandlerList_;
    private final List responseHandlerList_;
    private HubConnection connection_;
    private Metadata metadata_;
    private Subscriptions subscriptions_;
    private CallableClientServer clientServer_;
    private ConnectorCallableClient callable_;
    private final Logger logger_ =
        Logger.getLogger( HubConnector.class.getName() );

    public HubConnector( boolean callable ) {
        messageHandlerList_ = new ArrayList();
        responseHandlerList_ = new ArrayList();
        if ( callable ) {
            addMessageHandler( new CallHandler( "samp.hub.event.shutdown" ) {
                public Map processCall( HubConnection connection,
                                        String senderId, Message message ) {
                    String mtype = message.getMType();
                    assert "samp.hub.event.shutdown".equals( mtype );
                    checkHubMessage( connection, senderId, mtype );
                    hubShutdownEvent();
                    return null;
                }
            } );
            try {
                declareSubscriptions( computeSubscriptions() );
            }
            catch ( SampException e ) {
                // no connection yet - never mind
            }
        }
    }

    public HubConnector( boolean callable, Map metadata ) {
        this( callable );
        try {
            declareMetadata( metadata );
        }
        catch ( SampException e ) {
            throw new AssertionError( e );
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
        ensureCallable();
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
        ensureCallable();
        messageHandlerList_.add( handler );
    }

    public void removeMessageHandler( MessageHandler handler ) {
        messageHandlerList_.remove( handler );
    }

    public void addResponseHandler( ResponseHandler handler ) {
        ensureCallable();
        responseHandlerList_.add( handler );
    }

    public void removeResponseHandler( ResponseHandler handler ) {
        responseHandlerList_.remove( handler );
    }

    public boolean isConnected() {
        return connection_ != null;
    }

    public HubConnection getConnection() throws SampException {
        HubConnection connection = connection_;
        if ( connection == null ) {
            connection = createConnection();
            configureConnection( connection );
            connection_ = connection;
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

    private void ensureCallable() {
        boolean update = false;
        if ( callable_ == null ) {
            callable_ = new ConnectorCallableClient();
            update = true;
        }
        else if ( ! isConnected() ) {
            update = true;
        }
        try {
            if ( update ) {
                getConnection();  // sets callable as side-effect
            }
        }
        catch ( SampException e ) {
            // never mind
        }
    }

    private void hubShutdownEvent() {
        connection_ = null;
    }

    private void checkHubMessage( HubConnection connection, String senderId, 
                                  String mtype ) {
        if ( ! mtype.equals( connection.getRegInfo().getHubId() ) ) {
            logger_.warning( "Hub admin message " + mtype + " received from "
                           + "non-hub client.  Acting on it anyhow" );
        }
    }

    public static HubConnection createConnection() throws SampException {
        try {
            LockInfo lockInfo = LockInfo.readLockFile();
            lockInfo.check();
            return new XmlRpcHubConnection( lockInfo.getXmlrpcUrl(),
                                            lockInfo.getSecret() );
        }
        catch ( IOException e ) {
            throw new SampException( e );
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
            for ( Iterator it = responseHandlerList_.iterator();
                  it.hasNext(); ) {
                ResponseHandler handler = (ResponseHandler) it.next();
                handler.receiveResponse( conn_, responderId, msgTag, response );
            }
        }
    }
}
