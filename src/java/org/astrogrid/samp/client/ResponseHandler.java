package org.astrogrid.samp.client;

import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;

/**
 * Interface for a client which wishes to receive responses to message it
 * has sent asynchrnonously using <code>call</code> or <code>callAll</code>.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public interface ResponseHandler {

    /**
     * Indicates whether this handler will process the response with a
     * given <em>message tag</em>.
     *
     * @param  msgTag  tag with which earlier call was labelled
     * @return  true iff this handler wants to process the response labelled
     *          with <code>msgTag</code>
     */
    boolean ownsTag( String msgTag );

    /**
     * Processes a response to an earlier message.
     * Will only be called for <code>msgTag</code> values which return 
     * <code>true</code> from {@link #ownsTag}.
     *
     * @param  connection  hub connection
     * @param  responderId  client id of client sending response
     * @param  msgTag   message tag from previous call
     * @param  response  response object
     */
    void receiveResponse( HubConnection connection, String responderId,
                          String msgTag, Response response )
            throws SampException;
}
