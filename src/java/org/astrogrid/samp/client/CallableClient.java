package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;

/**
 * Defines callbacks which the hub can make on a callable client.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public interface CallableClient {

    /**
     * Receives a message for which no response is required.
     *
     * @param  senderId  public ID of sending client
     * @param  message   message
     */
    void receiveNotification( String senderId, Message message )
            throws SampException;

    /**
     * Receives a message for which a response is required.
     * The implementation must take care to call the hub's <code>reply</code>
     * method at some future point.
     *
     * @param  senderId  public ID of sending client
     * @param  msgId     message identifier for later use with reply
     * @param  message   message
     */
    void receiveCall( String senderId, String msgId, Message message )
            throws SampException;

    /**
     * Receives a response to a message previously sent by this client.
     *
     * @param  responderId  public ID of responding client
     * @param  msgTag     client-defined tag labelling previously-sent message
     * @param  response   returned response object
     */
    void receiveResponse( String responderId, String msgTag, Response response )
            throws SampException;
}
