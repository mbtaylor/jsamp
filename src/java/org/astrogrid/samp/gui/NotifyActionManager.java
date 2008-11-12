package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;

/**
 * SendActionManager subclass which works with messages of a single MType,
 * using the Notify delivery pattern.
 *
 * @author   Mark Taylor
 * @since    5 Sep 2008
 */
public abstract class NotifyActionManager extends SendActionManager {

    private final Component parent_;
    private final HubConnector connector_;
    private final String sendType_;
    private static final Logger logger_ =
        Logger.getLogger( NotifyActionManager.class.getName() );

    /**
     * Constructor.
     *
     * @param   parent  parent component
     * @param   connector  hub connector
     * @param   mtype   MType for messages transmitted by this object's actions
     * @param   sendType  short string identifying the kind of thing being
     *          sent (used for action descriptions etc)
     */
    public NotifyActionManager( Component parent, HubConnector connector,
                                String mtype, String sendType ) {
        super( connector, new SubscribedClientListModel( connector, mtype ) );
        parent_ = parent;
        connector_ = connector;
        sendType_ = sendType;
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
     * Called when a message has been sent by this object.
     * The default action is to notify via the logging system.
     * Subclasses may override this method.
     *
     * @param  connection  connection object
     * @param  msg  the message which was sent
     * @param  recipients  the recipients to whom an attempt was made to send
     *         the message
     */
    protected void messageSent( HubConnection connection, Message msg,
                                Client[] recipients ) {
        for ( int i = 0; i < recipients.length; i++ ) {
            logger_.info( "Message " + msg.getMType() + " sent to "
                                     + SampUtils.toString( recipients[ i ] ) );
        }
    }

    protected Action createBroadcastAction() {
        Action act = new AbstractAction() {
            public void actionPerformed( ActionEvent evt ) {
                List recipientIdList = null;
                Message msg = null;
                HubConnection connection = null;
                try {
                    msg = Message.asMessage( createMessage() );
                    msg.check();
                    connection = connector_.getConnection();
                    if ( connection != null ) {
                        recipientIdList = connection.notifyAll( msg );
                    }
                }
                catch ( Exception e ) {
                    ErrorDialog.showError( parent_, "Send Error",
                                           "Send failure " + e.getMessage(),
                                           e );
                }
                if ( recipientIdList != null ) {
                    assert connection != null;
                    assert msg != null;
                    List recipientList = new ArrayList();
                    Map clientMap = connector_.getClientMap();
                    for ( Iterator it = recipientIdList.iterator();
                          it.hasNext(); ) {
                        String id = (String) it.next();
                        Client recipient = (Client) clientMap.get( id );
                        if ( recipient != null ) {
                            recipientList.add( recipient );
                        }
                    }
                    messageSent( connection, msg,
                                (Client[])
                                recipientList.toArray( new Client[ 0 ] ) );
                }
            }
        };
        act.putValue( Action.NAME, "Broadcast " + sendType_ );
        act.putValue( Action.SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to all applications"
                    + " listening using the SAMP protocol" );
        act.putValue( Action.SMALL_ICON, getBroadcastIcon() );
        return act;
    }

    /**
     * Returns a new menu for targetted sends with a title suitable for
     * this object.
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
     * Action which performs a send.
     */
    private class SendAction extends AbstractAction {
        private final Client client_;
        private final String cName_;

        /**
         * Constructor.
         *
         * @param   client  target client
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
            try {
                msg = Message.asMessage( createMessage() );
                msg.check();
                connection = connector_.getConnection();
                if ( connection != null ) {
                    connection.notify( client_.getId(), msg );
                    sent = true;
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Send failure " + e.getMessage(), e );
            }
            if ( sent ) {
                assert connection != null;
                assert msg != null;
                messageSent( connection, msg, new Client[] { client_ } );
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
}
