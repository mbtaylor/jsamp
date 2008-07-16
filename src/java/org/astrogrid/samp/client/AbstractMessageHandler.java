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

/**
 * Partial implementation of MessageHandler interface which helps to ensure
 * correct client behaviour.
 * Concrete subclasses just need to specify the MTypes they subscribe to
 * and implement the {@link #processCall} method.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public abstract class AbstractMessageHandler implements MessageHandler {

    private Subscriptions subscriptions_;
    private final Logger logger_ =
        Logger.getLogger( AbstractMessageHandler.class.getName() );

    /**
     * Constructor using a given subscriptions map.
     *
     * @param  subscriptions  {@link org.astrogrid.samp.Subscriptions}-like map
     *                        defining which MTypes this handler can process
     */
    protected AbstractMessageHandler( Map subscriptions ) {
        setSubscriptions( subscriptions );
    }

    /**
     * Constructor using a given list of subscribed MTypes.
     * 
     * @param  mtypes  list of MTypes which this handler can process
     */
    protected AbstractMessageHandler( String[] mtypes ) {
        Map subs = new HashMap();
        for ( int i = 0; i < mtypes.length; i++ ) {
            subs.put( mtypes[ i ], new HashMap() );
        }
        setSubscriptions( subs );
    }

    /**
     * Constructor using a single subscribed MType.
     *
     * @param  mtype  single MType which this handler can process
     */
    protected AbstractMessageHandler( String mtype ) {
        this( new String[] { mtype } );
    }

    /**
     * Implements message processing.  Implementations should return a map
     * which contains the <code>samp.result</code> part of the call response,
     * that is the MType-specific return value name-&gt;value map.
     * As a special case, returning null is equivalent to returning an empty 
     * map.
     *
     * @param connection  hub connection
     * @param senderId  public ID of sender client
     * @param message  message with MType this handler is subscribed to
     * @return   MType-specific return name-&gt;value map;
     *           null may be used for an empty map
     */
    public abstract Map processCall( HubConnection connection, String senderId,
                                     Message message )
            throws Exception;

    /**
     * Sets the subscriptions map.  Usually this is called by the constructor,
     * but it may be reset manually.
     *
     * @param  subscriptions  {@link org.astrogrid.samp.Subscriptions}-like map
     *                        defining which MTypes this handler can process
     */
    public void setSubscriptions( Map subscriptions ) {
        Subscriptions subs = Subscriptions.asSubscriptions( subscriptions );
        subs.check();
        subscriptions_ = subs;
    }

    public Map getSubscriptions() {
        return subscriptions_;
    }

    /**
     * Calls {@link #processCall} and discards the result.
     */
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

    /**
     * Calls {@link #processCall} and uses its return value to send 
     * a reply back to the hub.
     */
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
