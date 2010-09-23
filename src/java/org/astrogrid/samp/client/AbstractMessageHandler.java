package org.astrogrid.samp.client;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampMap;
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
     * Implements message processing.  Implementations should normally
     * return a map which contains the <code>samp.result</code> part 
     * of the call response, that is the MType-specific return value
     * name-&gt;value map.
     * As a special case, returning null is equivalent to returning an empty 
     * map.
     * However, if {@link #createResponse} is overridden, the return value
     * semantics may be different.
     *
     * @param connection  hub connection
     * @param senderId  public ID of sender client
     * @param message  message with MType this handler is subscribed to
     * @return   result of handling this message; exact semantics determined
     *           by {@link #createResponse createResponse} implementation
     */
    public abstract Map processCall( HubConnection connection, String senderId,
                                     Message message )
            throws Exception;

    /**
     * Invoked by {@link #receiveCall receiveCall} to create a success 
     * response from the result of calling {@link #processCall processCall}.
     *
     * <p>The default implementation calls 
     * {@link Response#createSuccessResponse}(processOutput),
     * first transforming a null value to an empty map for convenience.
     * However, it may be overridden for more flexibility (for instance
     * in order to return non-OK responses).
     * 
     * @param  processOutput  a Map returned by {@link #processCall processCall}
     */
    protected Response createResponse( Map processOutput ) {
        Map result = processOutput == null ? SampMap.EMPTY : processOutput;
        return Response.createSuccessResponse( result );
    }

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
        catch ( Throwable e ) {
            logger_.log( Level.INFO,
                         "Error processing notification " + message.getMType()
                       + " - ignored", e );
        }
    }

    /**
     * Calls {@link #processCall}, generates a response from the result
     * using {@link #createResponse}, and sends the resulting response
     * as a reply to the hub.  In case of an exception, a suitable error 
     * response is sent instead.
     */
    public void receiveCall( HubConnection connection, String senderId,
                             String msgId, Message message )
            throws SampException {
        Response response;
        try {
            response =
                createResponse( processCall( connection, senderId, message ) );
        }
        catch ( Throwable e ) {
            response = Response.createErrorResponse( new ErrInfo( e ) );
        }
        connection.reply( msgId, response );
    }
}
