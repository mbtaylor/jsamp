package org.astrogrid.samp.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.SampException;

/**
 * CallableClient implementation used internally by the Web Profile hub.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
class WebCallableClient implements CallableClient {

    private final List queue_;
    private final int capacity_;
    private boolean ended_;

    /** Default maximum for queued callbacks. */
    public final static int DEFAULT_CAPACITY = 4096;

    /**
     * Constructs a callable client with default maximum capacity.
     */
    public WebCallableClient() {
        this( DEFAULT_CAPACITY );
    }

    /**
     * Constructs a callable client with a given maximum callback capacity.
     *
     * @param  capacity   maximum number of queued callbacks
     */
    public WebCallableClient( int capacity ) {
        capacity_ = capacity;
        queue_ = new ArrayList();
    }

    /**
     * Blocks for up to a given number of seconds or until any callbacks
     * are ready, then returns any ready callbacks.
     *
     * @param  timeout  timeout in seconds
     * @return   list of {@link Callback}-like Maps
     */
    public List pullCallbacks( int timeout ) throws SampException {

        // Calculate the timeout epoch as currentTimeMillis.
        long end = timeout >= 0 ? System.currentTimeMillis() + timeout * 1000
                                : Long.MAX_VALUE;

        // Wait until either there are callbacks or timeout is reached.
        try {
            synchronized ( queue_ ) {
                while ( queue_.isEmpty() &&
                        end - System.currentTimeMillis() > 0 &&
                        ! ended_ ) {
                    queue_.wait( end - System.currentTimeMillis() );
                }

                // Remove available callbacks from the queue, if any,
                // and return them.
                List callbacks = new ArrayList( queue_ );
                queue_.clear();
                return callbacks;
            }
        }
        catch ( InterruptedException e ) {
            throw new SampException( "Interrupted", e );
        }
    }

    public void receiveNotification( String senderId, Message message ) {
        enqueue( "receiveNotification",
                 new Object[] { senderId, message } );
    }

    public void receiveCall( String senderId, String msgId, Message message ) {
        enqueue( "receiveCall",
                 new Object[] { senderId, msgId, message } );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Response response ) {
        enqueue( "receiveResponse",
                 new Object[] { responderId, msgTag, response } );
    }

    /**
     * Informs this client that no further callbacks (receive* methods)
     * will be made on it.
     */
    public void endCallbacks() {
        ended_ = true;
        synchronized ( queue_ ) {
            queue_.notifyAll();
        }
    }

    /**
     * Adds a new callback to the queue which can be passed out via the
     * {@link #pullCallbacks} method.
     *
     * @param   methodName  callback method name
     * @param   params  callback parameter list
     */
    private void enqueue( String methodName, Object[] params ) {
        Callback callback =
            new Callback( WebClientProfile.WEBSAMP_CLIENT_PREFIX + methodName,
                          Arrays.asList( params ) );
        callback.check();
        synchronized ( queue_ ) {
            if ( queue_.size() < capacity_ ) {
                queue_.add( callback );
                queue_.notifyAll();
            }
            else {
                throw new IllegalStateException( "Callback queue is full"
                                               + " (" + capacity_
                                               + " objects)" );
            }
        }
    }
}
