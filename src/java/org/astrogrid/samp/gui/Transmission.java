package org.astrogrid.samp.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;

/**
 * Describes the properties of a message which has been sent from one
 * client to another.  Methods which might change the state of instances
 * of this class should be invoked only from the AWT event dispatch thread.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
public class Transmission {

    private final Client sender_;
    private final Client receiver_;
    private final Message msg_;
    private final String msgId_;
    private final long birthday_;
    private final List listenerList_;
    private final ChangeEvent evt_;
    private Response response_;
    private Throwable error_;
    private boolean senderUnreg_;
    private boolean receiverUnreg_;

    /**
     * Constructor.
     *
     * @param   sender  sender
     * @param   receiver  receiver
     * @param   msg   message
     * @param   msgId  message ID
     */
    public Transmission( Client sender, Client receiver, Message msg,
                         String msgId ) {
        sender_ = sender;
        receiver_ = receiver;
        msg_ = msg;
        msgId_ = msgId;
        birthday_ = System.currentTimeMillis();
        listenerList_ = new ArrayList();
        evt_ = new ChangeEvent( this );
    }

    /**
     * Returns the client which sent this transmission.
     *
     * @return   sender
     */
    public Client getSender() {
        return sender_;
    }

    /**
     * Returns the client to which this transmission was sent.
     *
     * @return  receiver
     */
    public Client getReceiver() {
        return receiver_;
    }

    /**
     * Returns the message which was sent.
     *
     * @return  message
     */
    public Message getMessage() {
        return msg_;
    }

    /**
     * Returns the message ID corresponding to this transmission.
     * Will be null for notify-type sends.
     *
     * @return   msg id
     */
    public String getMessageId() {
        return msgId_;
    }

    /**
     * Sets the response for this transmission.
     *
     * @param   response  response
     */
    public void setResponse( Response response ) {
        response_ = response;
        fireChange();
    }

    /**
     * Returns the response for this transmission.
     * Will be null if no response has (yet) arrived.
     *
     * @return   response
     */
    public Response getResponse() {
        return response_;
    }

    /**
     * Returns the message ID associated with this message.
     * This is the identifier passed to the receiver which it uses to
     * match messages with responses; it will be null iff the transmission 
     * used the <em>notify</em> delivery pattern (no response expected).
     *
     * @return   msgId; possibly null
     */
    public void fail( Throwable error ) {
        error_ = error;
        fireChange();
    }

    /**
     * Indicates that the sender of this transmission has unregistered.
     */
    public void setSenderUnregistered() {
        senderUnreg_ = true;
        fireChange();
    }

    /**
     * Indicates that the receiver of this transmission has unregistered.
     */
    public void setReceiverUnregistered() {
        receiverUnreg_ = true;
        fireChange();
    }

    /**
     * Indicates whether further changes to the state of this object 
     * are expected, that is if a response/failure is yet to be received.
     *
     * @return   true  iff no further changes are expected
     */
    public boolean isDone() {
        return error_ != null
            || response_ != null
            || msgId_ == null
            || receiverUnreg_;
    }

    /**
     * Adds a listener which will be notified if the state of this transmission
     * changes (if a response or failure is signalled).
     * The {@link javax.swing.event.ChangeEvent}s sent to these listeners
     * will have a source which is this Transmission.
     *
     * @param  listener  listener to add
     */
    public void addChangeListener( ChangeListener listener ) {
        listenerList_.add( listener );
    }

    /**
     * Removes a listener previously added by {@link #addChangeListener}.
     *
     * @param  listener  listener to remove
     */
    public void removeChangeListener( ChangeListener listener ) {
        listenerList_.remove( listener );
    }

    /**
     * Notifies listeners of a state change.
     */
    private void fireChange() {
        for ( Iterator it = listenerList_.iterator();
              it.hasNext(); ) {
            ChangeListener listener = (ChangeListener) it.next();
            listener.stateChanged( evt_ );
        }
    }
}
