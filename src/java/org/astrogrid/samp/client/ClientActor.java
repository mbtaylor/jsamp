package org.astrogrid.samp.client;

import java.util.Map;

/** 
 * Defines the methods which an XML-RPC callable client must implement.
 *
 * <p>This interface is an implementation detail, but it has to be public
 * because it's used in another package.  Rats.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */ 
public interface ClientActor {

    /**
     * Receives a message for which no response is required.
     *
     * @param  privateKey  private key for hub-client communication
     * @param  senderId  public ID of sending client
     * @param  msg       message
     */
    void receiveNotification( String privateKey, String senderId, Map msg );

    /**
     * Receives a message for which a response is required.
     * The implementation must take care to call the hub's <code>reply</code>
     * method at some future point.
     *
     * @param  privateKey  private key for hub-client communication
     * @param  senderId  public ID of sending client
     * @param  msgId     message identifier for later use with reply
     * @param  msg       message
     */
    void receiveCall( String privateKey, String senderId, String msgId,
                      Map msg );
    /**
     * Receives a response to a message previously sent by this client.
     *
     * @param  privateKey  private key for hub-client communication
     * @param  responderId  public ID of responding client
     * @param  msgTag     client-defined tag labelling previously-sent message
     * @param  response   returned response object
     */
    void receiveResponse( String privateKey, String responderId, String msgTag,
                          Map response );
}
