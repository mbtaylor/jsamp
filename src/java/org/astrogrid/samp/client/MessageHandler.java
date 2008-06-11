package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.SampException;

public interface MessageHandler {
    void receiveNotification( HubConnection connection,
                              String senderId, Message message )
            throws SampException;
    void receiveCall( HubConnection connection,
                      String senderId, String msgId, Message message )
            throws SampException;
    Map getSubscriptions();
}
