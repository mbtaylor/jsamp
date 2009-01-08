package org.astrogrid.samp.gui;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.Client;

/**
 * Manages actions to send SAMP messages to one or all recipients.
 * The main useful trick that this class can do is to maintain one or
 * more menus for sending messages to suitable recipients.
 * The contents of these menus are updated automatically depending on
 * the subscriptions of all the currently registered SAMP clients.
 *
 * <p>Note: concrete subclasses must call {@link #updateState} before use
 * (in the constructor).
 *
 * @author   Mark Taylor
 * @since    2 Sep 2008
 */
public abstract class SendActionManager {

    private final GuiHubConnector connector_;
    final ListModel subscribedClientModel_;
    private final List menuList_;
    private final ListDataListener subscriptionListener_;
    private final ChangeListener connectionListener_;
    private boolean enabled_;
    private Action broadcastAct_;
    private boolean broadcastActCreated_;
    private Action[] sendActs_;
    private static Icon SEND_ICON;
    private static Icon BROADCAST_ICON;
    private static final Logger logger_ =
        Logger.getLogger( SendActionManager.class.getName() );

    /** ComboBox element indicating broadcast to all clients. */
    public static final String BROADCAST_TARGET = "All Clients";

    /**
     * Constructor.
     *
     * @param  connector   hub connector
     * @param  clientListModel  list model containing only those
     *         clients which are suitable recipients;
     *         all elements must be {@link org.astrogrid.samp.Client}s
     */
    protected SendActionManager( GuiHubConnector connector,
                                 ListModel clientListModel ) {
        connector_ = connector;
        subscribedClientModel_ = clientListModel;
        subscriptionListener_ = new ListDataListener() {
            public void intervalAdded( ListDataEvent evt ) {
                updateState();
            }
            public void intervalRemoved( ListDataEvent evt ) {
                updateState();
            }
            public void contentsChanged( ListDataEvent evt ) {
                updateState();
            }
        };
        subscribedClientModel_.addListDataListener( subscriptionListener_ );

        // Ensure that changes to the connection status are reflected.
        connectionListener_ = new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateEnabledness();
            }
        };
        connector.addConnectionListener( connectionListener_ );

        // Initialise other state.
        enabled_ = true;
        menuList_ = new ArrayList();
    }

    /**
     * Returns a new action for broadcast associated with this object.
     * The enabled status of the action will be managed by this object.
     *
     * @return  broadcast action; may be null if broadcast is not required
     */
    protected abstract Action createBroadcastAction();

    /**
     * Returns an action which can perform a single-client send associated
     * with this object.  If it implements <code>equals</code> 
     * (and <code>hashCode</code>) intelligently there will be efficiency
     * advantages.
     * The enabled status of such actions will be managed by this object.
     *
     * @param   client   recipient client
     * @return   action which sends to the given client
     */
    protected abstract Action getSendAction( Client client );

    /**
     * Sets the enabled status of this object.  This acts as a restriction
     * (AND) on the enabled status of the menus and actions controlled by
     * this object.  If there are no suitable recipient applications
     * registered they will be disabled anyway.
     *
     * @param  enabled   false to ensure that the actions are disabled,
     *          true means they may be enabled
     */
    public void setEnabled( boolean enabled ) {
        enabled_ = enabled;
        updateEnabledness();
    }

    /**
     * Returns an action which will broadcast a message
     * to all suitable registered applications.
     *
     * <p>This action is currently not disabled when there are no suitable
     * listeners, mainly for debugging purposes (so you can see if a
     * message is getting sent and what it looks like even in absence of
     * suitable listeners).
     *
     * @return  broadcast action
     */
    public Action getBroadcastAction() {
        if ( ! broadcastActCreated_ ) {
            broadcastAct_ = createBroadcastAction();
            broadcastActCreated_ = true;
            updateEnabledness();
        }
        return broadcastAct_;
    }

    /**
     * Returns a new menu which provides options to send a message to
     * one of the registered listeners at a time.  This menu will be
     * disabled when no suitable listeners are registered.
     *
     * @param   name  menu title
     * @return   new message send menu
     */
    public JMenu createSendMenu( String name ) {
        JMenu menu = new JMenu( name );
        for ( int is = 0; is < sendActs_.length; is++ ) {
            menu.add( sendActs_[ is ] );
        }
        menuList_.add( menu );
        updateEnabledness();
        return menu;
    }

    /**
     * Releases any resources associated with a menu previously created
     * using {@link #createSendMenu}.  Don't use the menu again.
     *
     * @param  menu   previously created send menu
     */
    public void disposeSendMenu( JMenu menu ) {
        menuList_.remove( menu );
    }

    /**
     * Releases any resources associated with this object.
     */
    public void dispose() {
        subscribedClientModel_.removeListDataListener( subscriptionListener_ );
        if ( subscribedClientModel_ instanceof SubscribedClientListModel ) {
            ((SubscribedClientListModel) subscribedClientModel_).dispose();
        }
        connector_.removeConnectionListener( connectionListener_ );
    }

    /**
     * Updates the state of actions managed by this object when the 
     * list of registered listeners has changed.
     */
    public void updateState() {

        // Get a list of actions for the currently subscribed clients.
        int nsub = subscribedClientModel_.getSize();
        Action[] sendActs = new Action[ nsub ];
        for ( int ia = 0; ia < nsub; ia++ ) {
            sendActs[ ia ] =
                getSendAction( (Client)
                               subscribedClientModel_.getElementAt( ia ) );
        }

        // Update menus if required.
        if ( ! Arrays.equals( sendActs, sendActs_ ) ) {
            sendActs_ = sendActs;
            for ( Iterator menuIt = menuList_.iterator(); menuIt.hasNext(); ) {
                JMenu menu = (JMenu) menuIt.next();
                menu.removeAll();
                for ( int is = 0; is < sendActs.length; is++ ) {
                    menu.add( sendActs[ is ] );
                }
            }
            updateEnabledness();
        }
    }

    /**
     * Returns the client list to which this manager will offer sends.
     *
     * @return  listmodel whose elements are suitably subscribed {@link Client}s
     */
    public ListModel getClientListModel() {
        return subscribedClientModel_;
    }

    /**
     * Returns a new ComboBoxModel containing selections for each suitable
     * client and an additional selection for broadcast to all clients.
     * Elements are {@link org.astrogrid.samp.Client} objects, or 
     * {@link #BROADCAST_TARGET} to indicate broadcast.
     * The result of this is suitable for use with {@link #createTargetAction}.
     *
     * @return  new client combo box model
     */
    public ComboBoxModel createTargetSelector() {
        return new TargetComboBoxModel( subscribedClientModel_ );
    }

    /**
     * Returns an action suitable for sending the message represented by
     * this manager to a target selected by a supplied ComboBoxModel.
     * This model is typically the result of calling
     * {@link #createTargetSelector}.
     *
     * @param   targetSelector   combo box model in which the elements are
     *          {@link org.astrogrid.samp.Client} objects,
     *          or {@link #BROADCAST_TARGET} null to indicate broadcast
     */
    public Action createTargetAction( final ComboBoxModel targetSelector ) {
        return new AbstractAction( "Send to selected target" ) {
            public void actionPerformed( ActionEvent evt ) {
                Object target = targetSelector.getSelectedItem();
                if ( target instanceof Client ) {
                    getSendAction( (Client) target );
                }
                else if ( BROADCAST_TARGET.equals( target ) ) {
                    getBroadcastAction().actionPerformed( evt );
                }
                else {
                    Toolkit.getDefaultToolkit().beep();
                    logger_.warning( "Unknown send target: " + target
                                   + " - no action" );
                }
            }
        };
    }

    /**
     * Returns this manager's hub connector.
     *
     * @return  connector
     */
    public GuiHubConnector getConnector() {
        return connector_;
    }

    /**
     * Updates the enabled status of controlled actions in accordance with
     * this object's current state.
     */
    private void updateEnabledness() {
        boolean active = enabled_
                    && connector_.isConnected()
                    && sendActs_.length > 0;
        if ( broadcastAct_ != null ) {
            broadcastAct_.setEnabled( active );
        }
        for ( Iterator it = menuList_.iterator(); it.hasNext(); ) {
            ((JMenu) it.next()).setEnabled( active );
        }
    }

    /**
     * Returns an icon suitable for depicting a general targetted send.
     *
     * @return   send icon
     */
    public static Icon getSendIcon() {
        if ( SEND_ICON == null ) {
            SEND_ICON = IconStore.createResourceIcon( "phone2.gif" );
        }
        return SEND_ICON;
    }

    /**
     * Returns an icon suitable for depicting a general broadcast send.
     *
     * @return  broadcast icon
     */
    public static Icon getBroadcastIcon() {
        if ( BROADCAST_ICON == null ) {
            BROADCAST_ICON = IconStore.createResourceIcon( "tx3.gif" );
        }
        return BROADCAST_ICON;
    }

    /**
     * ComboBoxModel implementation used for selecting a target client.
     * It essentiall mirrors an existing client model but prepends a
     * broadcast option.
     */
    private static class TargetComboBoxModel extends AbstractListModel
                                             implements ComboBoxModel {
        private final ListModel clientListModel_;
        private Object selectedItem_ = BROADCAST_TARGET;

        /**
         * Constructor.
         *
         * @param  clientListModel  list model containing suitable
         *                          {@link org.astrogrid.samp.Client}s
         */
        TargetComboBoxModel( ListModel clientListModel ) {
            clientListModel_ = clientListModel;

            /* Watch the underlying client model for changes and
             * update this one accordingly. */
            clientListModel_.addListDataListener( new ListDataListener() {
                public void contentsChanged( ListDataEvent evt ) {
                    fireContentsChanged( evt.getSource(),
                                         adjustIndex( evt.getIndex0() ),
                                         adjustIndex( evt.getIndex1() ) );
                }
                public void intervalAdded( ListDataEvent evt ) {
                    fireIntervalAdded( evt.getSource(),
                                       adjustIndex( evt.getIndex0() ),
                                       adjustIndex( evt.getIndex1() ) );
                }
                public void intervalRemoved( ListDataEvent evt ) {
                    fireIntervalRemoved( evt.getSource(),
                                         adjustIndex( evt.getIndex0() ),
                                         adjustIndex( evt.getIndex1() ) );
                }
                private int adjustIndex( int index ) {
                    return index >= 0 ? index + 1
                                      : index;
                }
            } );
        }

        public int getSize() {
            return clientListModel_.getSize() + 1;
        }

        public Object getElementAt( int index ) {
            return index == 0 ? BROADCAST_TARGET
                              : clientListModel_.getElementAt( index - 1 );
        }

        public Object getSelectedItem() {
            return selectedItem_;
        }

        public void setSelectedItem( Object item ) {
            selectedItem_ = item;
        }
    }
}
