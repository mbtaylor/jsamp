package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.SampException;

/** 
 * This interface is an implementation detail, but it has to be visible
 * because it's used in another package.  Rats.
 */ 
public interface ClientActor {
    void receiveNotification( String privateKey, String senderId, Map msg )
            throws SampException;
    void receiveCall( String privateKey, String senderId, String msgId,
                      Map msg )
            throws SampException;
    void receiveResponse( String privateKey, String responsderId, String msgTag,
                          Map response )
            throws SampException;
}
