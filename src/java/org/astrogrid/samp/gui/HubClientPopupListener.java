package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.AbstractAction;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.HubClient;

/**
 * MouseListener which provides a popup menu with per-client options
 * for use with a JList containing HubClient objects.
 *
 * @author   Mark Taylor
 * @since    8 Jul 2009
 */
class HubClientPopupListener implements MouseListener {

    private final BasicHubService hub_;

    /** Message which does a ping. */
    private static final Message PING_MSG = new Message( "samp.app.ping" );

    /**
     * Constructor.
     *
     * @param  hub  hub service which knows about the HubClients contained
     *              in the JList this will be listening to
     */
    public HubClientPopupListener( BasicHubService hub ) {
        hub_ = hub;
    }

    public void mouseClicked( MouseEvent evt ) {
    }

    public void mouseEntered( MouseEvent evt ) {
    }

    public void mouseExited( MouseEvent evt ) {
    }

    public void mousePressed( MouseEvent evt ) {
        maybeShowPopup( evt );
    }

    public void mouseReleased( MouseEvent evt ) {
        maybeShowPopup( evt );
    }

    /**
     * Invoked for a MouseEvent which may be a popup menu trigger.
     *
     * @param   evt   popup trigger event candidate
     */
    private void maybeShowPopup( MouseEvent evt ) {
        if ( evt.isPopupTrigger() && evt.getSource() instanceof JList ) {
            final JList jlist = (JList) evt.getSource();
            final int index = jlist.locationToIndex( evt.getPoint() );
            if ( index >= 0 ) {
                Object item = jlist.getModel().getElementAt( index );
                if ( item instanceof HubClient ) {
                    HubClient client = (HubClient) item;

                    // Set the selection to the client for which the menu
                    // will be posted.  This is not essential, but it can be
                    // visually confusing for the user if it doesn't happen.
                    SwingUtilities.invokeLater( new Runnable() {
                        public void run() {
                            jlist.setSelectedIndex( index );
                        }
                    } );
                    Component comp = evt.getComponent();
                    JPopupMenu popper = createPopup( comp, client );
                    popper.show( comp, evt.getX(), evt.getY() );
                }
            }
        }
    }

    /**
     * Returns a new popup menu for a given client.
     * The actions on this menu are not dynamic (e.g. do not enable/disable
     * themselves according to changes in the hub status) because the
     * menu is likely to be short-lived.
     *
     * @param   parent  parent component
     * @param   client  hub client which the menu will affect
     * @return  new popup menu
     */
    private JPopupMenu createPopup( Component parent, HubClient client ) {
        JPopupMenu popper = new JPopupMenu();
        popper.add( new CallAction( parent, client, "Ping", PING_MSG, true ) );
        popper.add( new DisconnectAction( parent, client ) );
        return popper;
    }

    /**
     * Action which will forcibly disconnect a given client.
     */
    private class DisconnectAction extends AbstractAction {
        private final Component parent_;
        private final HubClient client_;

        /**
         * Constructor.
         *
         * @param   parent  parent component 
         * @param   client  client to disconnect
         */
        public DisconnectAction( Component parent, HubClient client ) {
            super( "Disconnect" );
            parent_ = parent;
            client_ = client;
            putValue( SHORT_DESCRIPTION,
                      "Forcibly disconnect client " + client_ + " from hub" );
            setEnabled( ! client.equals( hub_.getHubClient() ) );
        }

        public void actionPerformed( ActionEvent evt ) {
            try {
                hub_.disconnect( client_.getId(),
                                 "GUI hub user requested ejection" );
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Disconnect Error",
                                       "Error attempting to disconnect client "
                                     + client_, e );
            }
        }
    }

    /**
     * Action which will send a message to a client.
     */
    private class CallAction extends AbstractAction {
        private final Component parent_;
        private final HubClient client_;
        private final String name_;
        private final Message msg_;
        private final boolean isCall_;

        /**
         * Constructor.
         *
         * @param  parent  parent component
         * @param  client  client to receive message
         * @param  name    informal name of message (for menu)
         * @param  msg     message to send
         * @param  isCall  true for call, false for notify
         */
        public CallAction( Component parent, HubClient client, String name,
                           Message msg, boolean isCall ) {
            super( name );
            parent_ = parent;
            client_ = client;
            name_ = name;
            msg_ = msg;
            isCall_ = isCall;
            String mtype = msg.getMType();
            putValue( SHORT_DESCRIPTION,
                      "Send " + mtype + ( isCall ? " call" : " notification" )
                    + " to client " + client );
            setEnabled( client_.isSubscribed( mtype ) );
        }

        public void actionPerformed( ActionEvent evt ) {
            Object senderKey = hub_.getHubClient().getPrivateKey();
            String recipientId = client_.getId();
            try {
                if ( isCall_ ) {
                    hub_.call( senderKey, recipientId, name_ + "-tag", msg_ );
                }
                else {
                    hub_.notify( senderKy, recipientId, msg_ );
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, name_ + " Error",
                                       "Error attempting to send message "
                                     + msg_.getMType() + " to client "
                                     + client_, e );
            }
        }
    }
}
