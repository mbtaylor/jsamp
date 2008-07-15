package org.astrogrid.samp.hub;

import java.util.Map;
import org.astrogrid.samp.SampException;

/**
 * Interface describing how a hub can invoke callbacks on a client.
 * This corresponds to the callable client API in the SAMP standard.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface Receiver {

    /**
     * Receives a message not requiring a response from the hub.
     *
     * @param  senderId  sender public ID
     * @param  message   {@link org.astrogrid.samp.Message}-like map
     */
    void receiveNotification( String senderId, Map message )
            throws SampException;

    /** 
     * Receives a message requiring a response from the hub.
     *
     * @param  senderId  sender public ID
     * @param  msgId     hub-generated message identifier
     * @param  message   {@link org.astrogrid.samp.Message}-like map
     */
    void receiveCall( String senderId, String msgId, Map message )
            throws SampException;

    /**
     * Receives a response from the hub corresponding to a previously 
     * sent message.
     *
     * @param   responderId  responder public Id
     * @param   msgTag       sender-generated message tag
     * @param   response     {@link org.astrogrid.samp.Response}-like map
     */
    void receiveResponse( String responderId, String msgTag, Map response )
            throws SampException;
}
