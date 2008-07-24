package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Message;

/**
 * Interface for a client which wishes to receive messages.
 * In most cases it is more convenient to subclass the abstract class
 * {@link AbstractMessageHandler} than to implement this interface directly.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public interface MessageHandler {

    /**
     * Returns a Subscriptions map corresponding to the messages 
     * handled by this object.
     * Only messages with MTypes which match the keys of this map will
     * be passed to this object.
     *
     * @return  {@link org.astrogrid.samp.Subscriptions}-like map
     */
    Map getSubscriptions();

    /**
     * Processes a message which does not require a response.
     *
     * @param  connection  hub connection
     * @param  senderId    public ID of client which sent the message
     * @param  message     message
     */
    void receiveNotification( HubConnection connection,
                              String senderId, Message message );

    /**
     * Processes a message which does require a response.
     * Implementations should make sure that a subsequent call to 
     * <code>connection.reply()</code> is made using the 
     * supplied <code>msgId</code>.
     *
     * @param  connection  hub connection
     * @param  senderId    public ID of client which sent the message
     * @param  msgId       message ID
     * @param  message     message
     */
    void receiveCall( HubConnection connection,
                      String senderId, String msgId, Message message );
}
