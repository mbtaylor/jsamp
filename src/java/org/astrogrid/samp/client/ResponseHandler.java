package org.astrogrid.samp.client;

import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;

public interface ResponseHandler {
    void receiveResponse( HubConnection connection, String responderId,
                          String msgTag, Response response )
            throws SampException;
}
