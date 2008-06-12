package org.astrogrid.samp.client;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public abstract class AbstractMessageHandler implements MessageHandler {

    private Subscriptions subscriptions_;
    private final Logger logger_ =
        Logger.getLogger( AbstractMessageHandler.class.getName() );

    protected AbstractMessageHandler( Map subscriptions ) {
        setSubscriptions( subscriptions );
    }

    protected AbstractMessageHandler( String[] mtypes ) {
        Map subs = new HashMap();
        for ( int i = 0; i < mtypes.length; i++ ) {
            subs.put( mtypes[ i ], new HashMap() );
        }
        setSubscriptions( subs );
    }

    protected AbstractMessageHandler( String mtype ) {
        this( new String[] { mtype } );
    }

    public void setSubscriptions( Map subscriptions ) {
        Subscriptions subs = Subscriptions.asSubscriptions( subscriptions );
        subs.check();
        subscriptions_ = subs;
    }

    public Map getSubscriptions() {
        return subscriptions_;
    }

    public abstract Map processCall( HubConnection connection, String senderId,
                                     Message message )
        throws Exception;

    public void receiveNotification( HubConnection connection, String senderId,
                                     Message message ) {
        try {
            processCall( connection, senderId, message );
        }
        catch ( Exception e ) {
            logger_.log( Level.INFO,
                         "Error processing notification " + message.getMType()
                       + " - ignored", e );
        }
    }

    public void receiveCall( HubConnection connection, String senderId,
                             String msgId, Message message )
            throws SampException {
        Response response;
        try {
            Map result = processCall( connection, senderId, message ); 
            result = result == null ? new HashMap() : result;
            response = Response.createSuccessResponse( result );
        }
        catch ( Exception e ) {
            response = Response.createErrorResponse( new ErrInfo( e ) );
        }
        connection.reply( msgId, response );
    }
}
