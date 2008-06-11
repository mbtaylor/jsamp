package org.astrogrid.samp.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

    public HubConnector() {
        messageHandlerList_ = new ArrayList();
        responseHandlerList_ = new ArrayList();
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
            getConnection();  // sets callable as side-effect
        }
        catch ( SampException e ) {
            // never mind
        }
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
