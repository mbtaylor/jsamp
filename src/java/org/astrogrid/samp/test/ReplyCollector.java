package org.astrogrid.samp.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Partial implementation of {@link CallableClient} which handles the
 * <code>receiveReply</code> method.
 * This takes care of matching up replies with calls and is intended for
 * use with test classes.  Some assertions are made within this class
 * to check that replies match with messages sent.  Call-type messages
 * must be sent using this object's <code>call</code> and <code>callAll</code>
 * methods, rather than directly on the <code>HubConnection</code>,
 * to ensure that the internal state stays correct.
 *
 * @author   Mark Taylor
 * @since    18 Jul 2008
 */
abstract class ReplyCollector implements CallableClient {

    private final HubConnection connection_;
    private final Set sentSet_;
    private final Map replyMap_;

    /**
     * Constructor.
     *
     * @param  connection   hub connection 
     */
    public ReplyCollector( HubConnection connection ) {
        connection_ = connection;
        sentSet_ = Collections.synchronizedSet( new HashSet() );
        replyMap_ = Collections.synchronizedMap( new HashMap() );
    }

    /**
     * Performs a <code>call</code> method on this collector's hub connection.
     * Additional internal state is updated.
     * Although it is legal as far as SAMP goes, the <code>msgTag</code>
     * must not be one which was used earlier for the same recipient.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  message ID
     */
    public String call( String recipientId, String msgTag, Map msg )
            throws SampException {
        Object key = createKey( recipientId, msgTag );
        if ( sentSet_.contains( key ) ) {
            throw new IllegalArgumentException( "Key " + key + " reused" );
        }
        sentSet_.add( createKey( recipientId, msgTag ) );
        return connection_.call( recipientId, msgTag, msg );
    }

    /**
     * Performs a <code>callAll</code> method on this collector's
     * hub connection.
     * Additional internal state is updated.
     * Although it is legal as far as SAMP goes, the <code>msgTag</code>
     * must not be one which was used for an earlier broadcast.
     *
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  message ID
     */
    public Map callAll( String msgTag, Map msg ) throws SampException {
        Object key = createKey( null, msgTag );
        if ( sentSet_.contains( key ) ) {
            throw new IllegalArgumentException( "Key " + key + " reused" );
        }
        sentSet_.add( createKey( null, msgTag ) );
        return connection_.callAll( msgTag, msg );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Response response ) {
        Object key = createKey( responderId, msgTag );
        Object result;
        try {
            if ( replyMap_.containsKey( key ) ) {
                throw new TestException( "Response for " + key
                                       + " already received" );
            }
            else if ( ! sentSet_.contains( key ) &&
                      ! sentSet_.contains( createKey( null, msgTag ) ) ) {
                throw new TestException( "Message " + key + " never sent" );
            }
            result = response;
        }
        catch ( TestException e ) {
            result = e;
        }
        synchronized ( replyMap_ ) {
            replyMap_.put( key, result );
            replyMap_.notifyAll();
        }
    }

    /**
     * Returns the total number of unretrieved replies so far collected by
     * this object.
     *
     * @return  reply count
     */
    public int getReplyCount() {
        return replyMap_.size();
    }

    /**
     * Waits for a reply to a message sent earlier
     * using <code>call</code> or <code>callAll</code>.
     * Blocks until such a response is received.
     *
     * @param  responderId  client ID of client providing response
     * @param  msgTag   tag which was used to send the message
     * @return   response
     */
    public Response waitForReply( String responderId, String msgTag ) {
        Object key = createKey( responderId, msgTag );
        Object result;
        try {
            synchronized ( replyMap_ ) {
                while ( ! replyMap_.containsKey( key ) ) {
                    replyMap_.wait();
                }
                result = replyMap_.get( key );
            }
        }
        catch ( InterruptedException e ) {
            throw new Error( "Interrupted", e );
        }
        return getReply( responderId, msgTag );
    }

    /**
     * Gets the reply to a message sent earlier
     * using <code>call</code> or <code>callAll</code>.
     * Does not block; if no such response has been received so far,
     * returns null.
     *
     * @param  responderId  client ID of client providing response
     * @param  msgTag   tag which was used to send the message
     * @return   response
     */
    public Response getReply( String responderId, String msgTag ) {
        Object result = replyMap_.remove( createKey( responderId, msgTag ) );
        if ( result == null ) {
            return null;
        }
        else if ( result instanceof Response ) {
            return (Response) result;
        }
        else if ( result instanceof Throwable ) {
            throw new TestException( (Throwable) result );
        }
        else {
            throw new AssertionError();
        }
    }

    /**
     * Returns an opaque object suitable for use as a map key 
     * based on a recipient ID and message tag.
     *
     * @param   recipientId  recipient ID
     * @param   msgTag  message tag
     */
    private static Object createKey( String recipientId, String msgTag ) {
        return Arrays.asList( new String[] { recipientId, msgTag } );
    }
}
