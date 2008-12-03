package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;

/**
 * SendActionManager which uses the Asynchronous Call/Response delivery
 * pattern, but allows a "broadcast" to send different message objects
 * to different recipients.
 * Multiple targetted sends rather than an actual SAMP broadcast may be
 * used to achieve this.
 * Concrete subclasses need only implement the 
 * {@link #createMessage(org.astrogrid.samp.Client)} method.
 * They may also wish to to customise the returned Send and Broadcast Action
 * objects (for instance give them useful names and descriptions).
 *
 * @author   Mark Taylor
 * @since    3 Dec 2008
 */
public abstract class IndividualCallActionManager
                      extends AbstractCallActionManager {
    private final Component parent_;

    /**
     * Constructor.
     *
     * @param   parent  parent component
     * @param   connector  hub connector
     * @param   clientListModel  list model containing only those clients
     *          which are suitable recipients;
     *          all elements must be {@link Client}s
     */
    public IndividualCallActionManager( Component parent,
                                        GuiHubConnector connector,
                                        ListModel clientListModel ) {
        super( parent, connector, clientListModel );
        parent_ = parent;
    }

    protected abstract Map createMessage( Client client ) throws Exception;

    public Action createBroadcastAction() {
        return new BroadcastAction();
    }

    /**
     * Action which performs "broadcasts".  They may actually be multiple
     * targetted sends.
     */
    private class BroadcastAction extends AbstractAction {

        final HubConnector connector_ = getConnector();
        final ListModel clientList_ = getClientListModel();

        /**
         * Constructor.
         */
        BroadcastAction() {
            putValue( SMALL_ICON, getBroadcastIcon() );
        }

        public void actionPerformed( ActionEvent evt ) {

            // Identify groups of recipients which can receive the same
            // Message object as each other.
            int nc = clientList_.getSize();
            Map msgMap = new HashMap();
            try {
                for ( int ic = 0; ic < nc; ic++ ) {
                    Client client = (Client) clientList_.getElementAt( ic );
                    Map message = createMessage( client );
                    if ( message != null ) {
                        Message msg = Message.asMessage( message );
                        msg.check();
                        if ( ! msgMap.containsKey( msg ) ) {
                            msgMap.put( msg, new HashSet() );
                        }
                        Collection clientSet = (Collection) msgMap.get( msg );
                        clientSet.add( client );
                    }
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Error constructing message "
                                     + e.getMessage(), e );
                return;
            }

            // Send the message to each group at a time.
            try {
                HubConnection connection = connector_.getConnection();
                if ( connection != null ) {
                    for ( Iterator it = msgMap.entrySet().iterator();
                          it.hasNext(); ) {
                        Map.Entry entry = (Map.Entry) it.next();
                        Message msg = (Message) entry.getKey();
                        Client[] recipients =
                            (Client[]) ((Collection) entry.getValue())
                                      .toArray( new Client[ 0 ] );
                        String tag = createTag();
                        ResultHandler handler =
                            createResultHandler( connection, msg, recipients );
                        registerHandler( tag, recipients, handler );
                    }
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Error sending message "
                                     + e.getMessage(), e );
            }
        }
    }
}
