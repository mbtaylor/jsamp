package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;

public interface CallableClient {

    void receiveNotification( String senderId, Message message )
            throws SampException;

    void receiveCall( String senderId, String msgId, Message message )
            throws SampException;

    void receiveResponse( String responderId, String msgTag, Response response )
            throws SampException;
}
