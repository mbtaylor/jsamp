package org.astrogrid.samp.hub;

import java.util.Map;
import org.astrogrid.samp.SampException;

public interface Receiver {
    void receiveNotification( String senderId, Map message )
            throws SampException;
    void receiveCall( String senderId, String msgId, Map message )
            throws SampException;
    void receiveResponse( String responderId, String msgTag, Map response )
            throws SampException;
}
