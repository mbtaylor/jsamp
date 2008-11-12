package org.astrogrid.samp.gui;

import org.astrogrid.samp.Client;
import org.astrogrid.samp.Response;

/**
 * Interface for use with {@link CallActionManager} which consumes call
 * responses.
 *
 * @author   Mark Taylor
 * @since    12 Nov 2008
 */
public interface ResultHandler {

    /**
     * Called when a response is received from a client to which the message
     * was sent.
     *
     * @param   client  responder client
     * @return  response  content of response
     */
    public void result( Client client, Response response );

    /**
     * Called when no more {@link #result} invocations will be made,
     * either because all have been received or for some other reason,
     * such as the hub shutting down.
     */
    public void done();
}
