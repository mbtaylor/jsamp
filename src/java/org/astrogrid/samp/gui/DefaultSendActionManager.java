package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;

/**
 * SendActionManager concrete subclass which works with messages of a
 * single MType.
 *
 * @author   Mark Taylor
 * @since    5 Sep 2008
 */
public abstract class DefaultSendActionManager extends SendActionManager {

    private final Component parent_;
    private final HubConnector connector_;
    private final String sendType_;
    private final Icon SEND_ICON = createIcon( "phone2.gif" );
    private final Icon BROADCAST_ICON = createIcon( "tx3.gif" );
    private static final Logger logger_ =
        Logger.getLogger( DefaultSendActionManager.class.getName() );

    /**
     * Constructor.
     *
     * @param   parent  parent component
     * @param   connector  hub connector
     * @param   mtype   MType for messages transmitted by this object's actions
     * @param   sendType  short string identifying the kind of thing being
     *          sent (used for action descriptions etc)
     */
    public DefaultSendActionManager( Component parent, HubConnector connector,
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

    protected Action createBroadcastAction() {
        Action act = new AbstractAction() {
            public void actionPerformed( ActionEvent evt ) {
                try {
                    Map msg = createMessage();
                    HubConnection connection = connector_.getConnection();
                    if ( connection != null ) {
                        connection.notifyAll( msg );
                    }
                }
                catch ( Exception e ) {
                    ErrorDialog.showError( parent_, "Send Error",
                                           "Send failure " + e.getMessage(),
                                           e );
                }
            }
        };
        act.putValue( Action.NAME, "Broadcast " + sendType_ );
        act.putValue( Action.SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to all applications"
                    + " listening using the SAMP protocol" );
        act.putValue( Action.SMALL_ICON, BROADCAST_ICON );
        return act;
    }

    /**
     * Constructs a menu with a sensible name and icon.
     *
     * @return  new menu
     */
    public JMenu createSendMenu() {
        JMenu menu = super.createSendMenu( "Send " + sendType_ + " to..." );
        menu.setIcon( SEND_ICON );
        return menu;
    }

    protected Action getSendAction( Client client ) {
        return new SendAction( client );
    }

    /**
     * Constructs an icon given a file name in the images directory.
     *
     * @param  fileName  file name omitting directory
     * @return  icon
     */
    private static Icon createIcon( String fileName ) {
        String relLoc = "images/" + fileName;
        URL resource = Client.class.getResource( relLoc );
        if ( resource != null ) {
            return new ImageIcon( resource );
        }
        else {
            logger_.warning( "Failed to load icon " + relLoc );
            return new Icon() {
                public int getIconWidth() {
                    return 24;
                }
                public int getIconHeight() {
                    return 24;
                }
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                }
            };
        }
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
            String cName = null;
            Metadata meta = client.getMetadata();
            if ( meta != null ) {
                cName = meta.getName();
            }
            cName_ = cName == null || cName.trim().length() == 0
                   ? ( "client " + client.getId() )
                   : cName;
            putValue( NAME, cName_ );
            putValue( SHORT_DESCRIPTION,
                      "Transmit " + sendType_ + " to " + cName_
                    + " using SAMP protocol" );
        }

        public void actionPerformed( ActionEvent evt ) {
            try {
                Map msg = createMessage();
                HubConnection connection = connector_.getConnection();
                if ( connection != null ) {
                    connection.notify( client_.getId(), msg );
                }
            }
            catch ( Exception e ) {
                ErrorDialog.showError( parent_, "Send Error",
                                       "Send failure " + e.getMessage(), e );
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
