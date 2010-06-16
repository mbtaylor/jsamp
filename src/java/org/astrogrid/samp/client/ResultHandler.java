package org.astrogrid.samp.client;

import org.astrogrid.samp.Client;
import org.astrogrid.samp.Response;

/**
 * Interface which consumes call responses.
 *
 * @author   Mark Taylor
 * @since    12 Nov 2008
 */
public interface ResultHandler {

    /**
     * Called when a response is received from a client to which the message
     * was sent.
     *
     * @param   responder  responder client
     * @param   response  content of response
     */
    public void result( Client responder, Response response );

    /**
     * Called when no more {@link #result} invocations will be made,
     * either because all have been received or for some other reason,
     * such as the hub shutting down.
     */
    public void done();
}
