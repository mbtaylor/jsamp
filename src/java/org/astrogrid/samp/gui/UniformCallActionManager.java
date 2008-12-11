package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.ResultHandler;

/**
 * SendActionManager subclass which works with messages of a single MType,
 * using the Aysnchronous Call/Response delivery pattern.
 * Concrete subclasses need only implement {@link #createMessage()}.
 *
 * @author   Mark Taylor
 * @since    11 Nov 2008
 */
public abstract class UniformCallActionManager
                      extends AbstractCallActionManager {

    private final Component parent_;
    private final String mtype_;
    private final String sendType_;

    /**
     * Constructor.
     *
     * @param   parent  parent component
     * @param   connector  hub connector
     * @param   mtype   MType for messages transmitted by this object's actions
     * @param   sendType  short string identifying the kind of thing being
     *          sent (used for action descriptions etc)
     */
    public UniformCallActionManager( Component parent,
                                     GuiHubConnector connector,
                                     String mtype, String sendType ) {
        super( parent, connector,
               new SubscribedClientListModel( connector, mtype ) );
        parent_ = parent;
        mtype_ = mtype;
        sendType_ = sendType;
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
     * Implemented simply by calling {@link #createMessage()}.
     */
    protected Map createMessage( Client client ) throws Exception {
        return createMessage();
    }

    protected Action createBroadcastAction() {
        return new BroadcastAction();
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

    public Action getSendAction( Client client ) {
        Action action = super.getSendAction( client );
        action.putValue( Action.SHORT_DESCRIPTION,
                         "Transmit to " + client + " using SAMP " + mtype_ );
        return action;
    }

    /**
     * Action for sending broadcast messages.
     */
    private class BroadcastAction extends AbstractAction {

        /**
         * Constructor.
         */
        BroadcastAction() {
            putValue( NAME, "Broadcast " + sendType_ );
            putValue( SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to all applications"
                    + " listening using the SAMP protocol" );
            putValue( SMALL_ICON, getBroadcastIcon() );
        }

        public void actionPerformed( ActionEvent evt ) {
            HubConnector connector = getConnector();
            Set recipientIdSet = null;
            Message msg = null;
            HubConnection connection = null;
            String tag = null;

            // Attempt to send the message.
            try {
                msg = Message.asMessage( createMessage() );
                msg.check();
                connection = connector.getConnection();
                if ( connection != null ) {
                    tag = createTag();
                    recipientIdSet = connection.callAll( tag, msg ).keySet();
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Send failure " + e.getMessage(), e );
            }

            // If it was sent, arrange for the results to be passed to
            // a suitable result handler.
            if ( recipientIdSet != null ) {
                assert connection != null;
                assert msg != null;
                assert tag != null;
                List recipientList = new ArrayList();
                Map clientMap = connector.getClientMap();
                for ( Iterator it = recipientIdSet.iterator(); it.hasNext(); ) {
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
                registerHandler( tag, recipients, handler );
            }
        }
    }
}
