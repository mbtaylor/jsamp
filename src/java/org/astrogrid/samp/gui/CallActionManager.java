package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.ResponseHandler;

/**
 * SendActionManager subclass which works with messages of a single MType,
 * using the Aysnchronous Call/Response delivery pattern.
 *
 * @author   Mark Taylor
 * @since    11 Nov 2008
 */
public abstract class CallActionManager extends SendActionManager {

    private final Component parent_;
    private final HubConnector connector_;
    private final String sendType_;
    private final CallResponseHandler responder_;
    private static final Logger logger_ =
        Logger.getLogger( CallActionManager.class.getName() );

    /**
     * Constructor.
     * 
     * @param   parent  parent component
     * @param   connector  hub connector
     * @param   mtype   MType for messages transmitted by this object's actions
     * @param   sendType  short string identifying the kind of thing being
     *          sent (used for action descriptions etc)
     */
    public CallActionManager( Component parent, HubConnector connector,
                              String mtype, String sendType ) {
        super( connector, new SubscribedClientListModel( connector, mtype ) );
        parent_ = parent;
        connector_ = connector;
        sendType_ = sendType;
        responder_ = new CallResponseHandler();
        connector_.addResponseHandler( responder_ );
        connector_.addConnectionListener( responder_ );
        updateState();
    }

    /**
     * Generates the message which is sent to one or all clients
     * by this object's actions.
     *
     * @return   {@link org.astrogrid.samp.Message}-like Map representing
     *           message to transmit
     */
    protected abstract Map createMessage() throws Exception;

    /**
     * Returns an object which will be informed of the results of a single-
     * or multiple-recipient send as they arrive.
     * This method will be called from the event dispatch thread.
     *
     * <p>The default implementation returns an instance of
     * {@link PopupResultHandler}.
     *
     * @param  connection  connection object
     * @param  msg  the message which was sent
     * @param  recipients  the recipients to whom the message was sent
     * @return    result handler object
     */
    protected ResultHandler createResultHandler( HubConnection connection,
                                                 Message msg,
                                                 Client[] recipients ) {
        String title = "SAMP " + sendType_ + " Send";
        return new PopupResultHandler( parent_, "SAMP " + sendType_ + " Send",
                                       msg, recipients, 1 );
    }

    /**
     * Releases resources associated with this object.
     * Specifically, it removes listeners from the hub connector.
     * Following a call to this method, this object should not be used again.
     */
    public void dispose() {
        connector_.removeResponseHandler( responder_ );
        connector_.removeConnectionListener( responder_ );
    }

    protected Action createBroadcastAction() {
        Action act = new AbstractAction() {
            public void actionPerformed( ActionEvent evt ) {
                Set recipientIdSet = null;
                Message msg = null;
                HubConnection connection = null;
                String tag = null;

                // Attempt to send the message.
                try {
                    msg = Message.asMessage( createMessage() );
                    msg.check();
                    connection = connector_.getConnection();
                    if ( connection != null ) {
                        tag = responder_.createTag();
                        recipientIdSet =
                            connection.callAll( tag, msg ).keySet();
                    }
                }
                catch ( Exception e ) {
                    ErrorDialog.showError( parent_, "Send Error",
                                           "Send failure " + e.getMessage(),
                                           e );
                }

                // If it was sent, arrange for the results to be passed to
                // a suitable result handler.
                if ( recipientIdSet != null ) {
                    assert connection != null;
                    assert msg != null;
                    assert tag != null;
                    List recipientList = new ArrayList();
                    Map clientMap = connector_.getClientMap();
                    for ( Iterator it = recipientIdSet.iterator();
                          it.hasNext(); ) {
                        String id = (String) it.next();
                        Client recipient = (Client) clientMap.get( id );
                        if ( recipient != null ) {
                            recipientList.add( recipient );
                        }
                    }
                    Client[] recipients =
                        (Client[]) recipientList.toArray( new Client[ 0 ] );
                    ResultHandler handler =
                        createResultHandler( connection, msg, recipients );
                    if ( recipients.length == 0 ) {
                        if ( handler != null ) {
                            handler.done();
                        }
                        handler = null;
                    }
                    responder_.registerHandler( tag, recipients, handler );
                }
            }
        };

        // Configure the action further and return.
        act.putValue( Action.NAME, "Broadcast " + sendType_ );
        act.putValue( Action.SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to all applications"
                    + " listening using the SAMP protocol" );
        act.putValue( Action.SMALL_ICON, getBroadcastIcon() );
        return act;
    }

    /**
     * Returns a new targetted send menu with a title suitable for this object.
     *
     * @return  new send menu
     */
    public JMenu createSendMenu() {
        JMenu menu = super.createSendMenu( "Send " + sendType_ + " to..." );
        menu.setIcon( getSendIcon() );
        return menu;
    }

    protected Action getSendAction( Client client ) {
        return new SendAction( client );
    }

    /** 
     * Action which performs a send to a particular client.
     */
    private class SendAction extends AbstractAction {
        private final Client client_;
        private final String cName_;

        /**
         * Constructor.
         *
         * @param  client  target client
         */
        SendAction( Client client ) {
            client_ = client;
            cName_ = SampUtils.toString( client );
            putValue( NAME, cName_ );
            putValue( SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to " + cName_
                    + " using SAMP protocol" );
        }

        public void actionPerformed( ActionEvent evt ) {
            boolean sent = false;
            Message msg = null;
            HubConnection connection = null;
            String tag = null;

            // Attempt to send the messsage.
            try {
                msg = Message.asMessage( createMessage() );
                msg.check();
                connection = connector_.getConnection();
                if ( connection != null ) {
                    tag = responder_.createTag();
                    connection.call( client_.getId(), tag, msg );
                    sent = true;
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Send failure " + e.getMessage(), e );
            }

            // If it was sent, arrange for the result to be processed by 
            // a suitable result handler.
            if ( sent ) {
                assert connection != null;
                assert msg != null;
                assert tag != null;
                Client[] recipients = new Client[] { client_ };
                ResultHandler handler =
                    createResultHandler( connection, msg, recipients );
                responder_.registerHandler( tag, recipients, handler );
            }
        }

        public boolean equals( Object o ) {
            if ( o instanceof SendAction ) {
                SendAction other = (SendAction) o;
                return this.client_.equals( other.client_ )
                    && this.cName_.equals( other.cName_ );
            }
            else {
                return false;
            }
        }

        public int hashCode() {
            return client_.hashCode() * 23 + cName_.hashCode();
        }
    }

    /**
     * ResponseHandler implementation for use by this class.
     * It handles all SAMP responses for calls which have been made by
     * this object and passes them on to the appropriate ResultHandlers.
     */
    private class CallResponseHandler implements ResponseHandler,
                                                 ChangeListener {
        private int iCall_;
        private final Map tagMap_;

        /**
         * Constructor.
         */
        CallResponseHandler() {
            tagMap_ = Collections.synchronizedMap( new HashMap() );
        }

        /**
         * Creates and returns a new tag which will be attached to 
         * an outgoing message, and updates internal structures so that
         * it will be recognised in the future.
         * A subsequent call to {@link #registerHandler} should be made for the
         * returned tag.
         *
         * @return  new tag
         */
        public synchronized String createTag() {
            String tag = connector_.createTag( this );
            tagMap_.put( tag, null );
            return tag;
        }

        /**
         * Registers a result handler to handle results corresponding to a
         * message tag.
         * 
         * @param   tag   tag returned by an earlier invocation of 
         *                {@link #createTag}
         * @param   recipients  clients from which responses are expected
         * @param   handler    result handler for responses; may be null
         *                     if no handling is required
         */
        public void registerHandler( String tag, Client[] recipients,
                                     ResultHandler handler ) {
            synchronized ( tagMap_ ) {
                if ( handler != null ) {
                    tagMap_.put( tag, new TagInfo( recipients, handler ) );
                }
                else {
                    tagMap_.remove( tag );
                }
                tagMap_.notifyAll();
            }
        }

        public boolean ownsTag( String tag ) {
            return tagMap_.containsKey( tag );
        }

        public void receiveResponse( HubConnection connection,
                                     final String responderId, final String tag,
                                     final Response response ) {
            synchronized ( tagMap_ ) {
                if ( tagMap_.containsKey( tag ) ) {

                    // If the result handler is already registered, pass the
                    // result on to it.
                    TagInfo info = (TagInfo) tagMap_.get( tag );
                    if ( info != null ) {
                        processResponse( tag, info, responderId, response );
                    }

                    // If the response was received very quickly, it's possible
                    // that the handler has not been registered yet.
                    // In this case, wait until it is.
                    // Do this in a separate thread so that the 
                    // receiveResponse can return quickly (not essential, but
                    // good behaviour).
                    else {
                        new Thread( "TagWaiter-" + tag ) {
                            public void run() {
                                TagInfo tinfo;
                                try {
                                    synchronized ( tagMap_ ) {
                                        do {
                                            tinfo =
                                                (TagInfo) tagMap_.get( tag );
                                            if ( tinfo == null ) {
                                                tagMap_.wait();
                                            }
                                        } while ( tinfo == null );
                                    }
                                    processResponse( tag, tinfo, responderId,
                                                     response );
                                }
                                catch ( InterruptedException e ) {
                                    logger_.warning( "Interrupted??" );
                                }
                            }
                        }.start();
                    }
                }

                // Shouldn't happen - HubConnector should not have invoked
                // in this case.
                else {
                    logger_.warning( "Receive response for unknown tag "
                                   + tag + "??" );
                    return;
                }
            }
        }

        /**
         * Does the work of passing on a received response to a registered
         * result handler.
         *
         * @param  tag  message tag
         * @param  info   tag handling information object
         * @param  responderId  client ID of responder
         * @param  response  response object
         */
        private void processResponse( String tag, TagInfo info,
                                      String responderId, Response response ) {
            ResultHandler handler = info.handler_;
            Map recipientMap = info.recipientMap_;
            synchronized ( info ) {
                Client responder = (Client) recipientMap.remove( responderId );

                // Pass response on to handler.
                if ( responder != null ) {
                    handler.result( responder, response );
                }

                // If there are no more to come, notify the handler of this.
                if ( recipientMap.isEmpty() ) {
                    handler.done();
                }
            }

            // Unregister the handler if no more responses are expected for it.
            synchronized ( tagMap_ ) {
                if ( recipientMap.isEmpty() ) {
                    tagMap_.remove( tag );
                }
            }
        }

        public void stateChanged( ChangeEvent evt ) {
            if ( ! connector_.isConnected() ) {
                hubDisconnected();
            }
        }

        /**
         * Called when the connection to the hub disappears.
         */
        private void hubDisconnected() {
            synchronized ( tagMap_ ) {

                // Notify all result handlers that they will receive no more
                // responses, then unregister them all.
                for ( Iterator it = tagMap_.entrySet().iterator();
                      it.hasNext(); ) {
                    Map.Entry entry = (Map.Entry) it.next();
                    String tag = (String) entry.getKey();
                    TagInfo info = (TagInfo) entry.getValue();
                    if ( info != null ) {
                        info.handler_.done();
                    }
                    it.remove();
                }
            }
        }
    }

    /**
     * Aggregates information required for handling responses which 
     * correspond to a particular message tag.
     */
    private static class TagInfo {
        final Map recipientMap_;
        final ResultHandler handler_;

        /**
         * Constructor.
         *
         * @param  recipients  recipients of message
         * @param  handler   handler for responses
         */
        public TagInfo( Client[] recipients, ResultHandler handler ) {
            recipientMap_ = Collections.synchronizedMap( new HashMap() );
            for ( int i = 0; i < recipients.length; i++ ) {
                Client recipient = recipients[ i ];
                recipientMap_.put( recipient.getId(), recipient );
            }
            handler_ = handler;
        }
    }
}
