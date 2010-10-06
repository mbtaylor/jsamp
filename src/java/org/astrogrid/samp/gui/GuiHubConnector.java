package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.client.TrackedClientSet;
import org.astrogrid.samp.xmlrpc.HubMode;
import org.astrogrid.samp.xmlrpc.HubRunner;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Extends HubConnector to provide additional graphical functionality.
 * In particular Swing {@link javax.swing.Action}s are provided for 
 * hub connection/disconnection
 * and the client list is made available as a {@link javax.swing.ListModel}.
 * See the {@link org.astrogrid.samp.client.HubConnector superclass} 
 * documentation for details of how to use this class.
 * A number of utility methods build on these features to provide
 * Swing components and Actions which can be used directly to populate
 * application menus etc.
 *
 * @author   Mark Taylor
 * @since    25 Nov 2008
 */
public class GuiHubConnector extends HubConnector {

    private final ListModel clientListModel_;
    private final List connectionListenerList_;
    private final Map updateMap_;
    private boolean wasConnected_;
    static ConnectionUpdate ENABLE_ACTION = new ConnectionUpdate() {
        public void setConnected( Object action, boolean isConnected ) {
            ((Action) action).setEnabled( isConnected );
        }
    };
    static ConnectionUpdate DISABLE_ACTION = new ConnectionUpdate() {
        public void setConnected( Object action, boolean isConnected ) {
            ((Action) action).setEnabled( ! isConnected );
        }
    };
    static ConnectionUpdate REPAINT_COMPONENT = new ConnectionUpdate() {
        public void setConnected( Object comp, boolean isConnected ) {
            ((Component) comp).repaint();
        }
    };
    static ConnectionUpdate ENABLE_COMPONENT = new ConnectionUpdate() {
        public void setConnected( Object comp, boolean isConnected ) {
            ((Component) comp).setEnabled( isConnected );
        }
    };

    /**
     * Constructs a hub connector based on a given profile instance.
     *
     * @param  profile  profile implementation
     */
    public GuiHubConnector( ClientProfile profile ) {
        super( profile, new ListModelTrackedClientSet() );
        clientListModel_ = (ListModelTrackedClientSet) getClientSet();
        connectionListenerList_ = new ArrayList();
        updateMap_ = new WeakHashMap();

        // Update state when hub connection starts/stops.
        addConnectionListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateConnectionState();
            }
        } );
        updateConnectionState();
    }

    protected void connectionChanged( boolean isConnected ) {
        super.connectionChanged( isConnected );
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                ChangeEvent evt = new ChangeEvent( GuiHubConnector.this );
                for ( Iterator it = connectionListenerList_.iterator();
                      it.hasNext(); ) {
                    ((ChangeListener) it.next()).stateChanged( evt );
                }
            }
        } );
    }

    /**
     * Adds a listener which will be notified when this connector
     * registers or unregisters with a hub.
     *
     * @param  listener   listener to add
     */
    public void addConnectionListener( ChangeListener listener ) {
        connectionListenerList_.add( listener );
    }

    /**
     * Removes a listener previously added by
     * {@link #addConnectionListener addConnectionListener}.
     *
     * @param   listener  listener to remove
     */
    public void removeConnectionListener( ChangeListener listener ) {
        connectionListenerList_.remove( listener );
    }

    /**
     * Returns a ListModel containing the registered clients.
     * Listeners to this model are correctly notified whenever any change
     * in its contents takes place.
     * 
     * @return  list model containing {@link Client} objects
     */
    public ListModel getClientListModel() {
        return clientListModel_;
    }

    /**
     * Returns a list cell renderer suitable for use with the 
     * client list model returned by {@link #getClientListModel}.
     *
     * @return   list cell renderer for Client objects
     */
    public ListCellRenderer createClientListCellRenderer() {
        return new ClientListCellRenderer();
    }

    /**
     * Returns an action which attempts to register with the hub.
     * Disabled when already registered.
     *
     * @return  registration action
     */
    public Action createRegisterAction() {
        Action regAct = new RegisterAction( true );
        registerUpdater( regAct, DISABLE_ACTION );
        return regAct;
    }

    /**
     * Returns an action which attempts to unregister from the hub.
     * Disabled when already unregistered.
     *
     * @return  unregistration action
     */
    public Action createUnregisterAction() {
        Action unregAct = new RegisterAction( false ); 
        registerUpdater( unregAct, ENABLE_ACTION );
        return unregAct;
    }

    /**
     * Returns an action which toggles hub registration.
     *
     * @return   registration toggler action
     */
    public Action createToggleRegisterAction() {
        RegisterAction toggleRegAct = new RegisterAction();
        registerUpdater( toggleRegAct, new ConnectionUpdate() {
            public void setConnected( Object item, boolean isConnected ) {
                ((RegisterAction) item).setSense( ! isConnected );
            }
        } );
        return toggleRegAct;
    }

    /**
     * Returns a new action which will register with a hub if one is running,
     * and if not, will offer to start a hub.
     * The exact options for starting a hub are given by the 
     * <code>hubStartActions</code> parameter - the elements of this array
     * will normally be generated by calling the 
     * {@link #createHubAction createHubAction} method.
     *
     * @param  parent  parent component, used for placing dialogue
     * @param  hubStartActions  actions which start a hub,
     *                          or null for a default list
     */
    public Action createRegisterOrHubAction( final Component parent,
                                             Action[] hubStartActions ) {
        final Action[] hubActs;
        if ( hubStartActions != null ) {
            hubActs = hubStartActions;
        }
        else {
            HubMode internalMode = SysTray.getInstance().isSupported()
                                 ? HubMode.CLIENT_GUI
                                 : HubMode.NO_GUI;
            hubActs = new Action[] {
                createHubAction( false, internalMode ),
                createHubAction( true, HubMode.MESSAGE_GUI ),
            };
        }
        Action regAct = new RegisterAction() {
            protected void registerFailed() {
                Object msg = new String[] {
                    "No SAMP hub is running.",
                    "You may start a hub if you wish.",
                };
                List buttList = new ArrayList();
                JButton[] options = new JButton[ hubActs.length + 1 ];
                for ( int i = 0; i < hubActs.length; i++ ) {
                    options[ i ] = new JButton( hubActs[ i ] );
                }
                options[ hubActs.length ] = new JButton( "Cancel" );
                final JDialog dialog =
                    new JOptionPane( msg, JOptionPane.WARNING_MESSAGE,
                                     JOptionPane.DEFAULT_OPTION,
                                     null, options, null )
                   .createDialog( parent, "No Hub" );
                ActionListener closeListener = new ActionListener() {
                    public void actionPerformed( ActionEvent evt ) {
                        dialog.dispose();
                    }
                };
                for ( int iopt = 0; iopt < options.length; iopt++ ) {
                    options[ iopt ].addActionListener( closeListener );
                }
                dialog.show();
            }
        };
        registerUpdater( regAct, new ConnectionUpdate() {
            public void setConnected( Object item, boolean isConnected ) {
                ((RegisterAction) item).setSense( ! isConnected );
            }
        } );
        return regAct;
    }

    /**
     * Returns an action which will display a SAMP hub monitor window.
     *
     * @return   monitor window action
     */
    public Action createShowMonitorAction() {
        return new MonitorAction();
    }

    /**
     * Returns an action which will start up a SAMP hub.
     * You can specify whether it runs in the current JVM or a newly
     * created one; in the former case, it will shut down when the
     * current application does.
     *
     * @param   external  false to run in the current JVM,
     *                    true to run in a new one
     * @param   hubMode   hub mode
     */
    public Action createHubAction( boolean external, HubMode hubMode ) {
        return new HubAction( external, hubMode );
    }

    /**
     * Creates a component which indicates whether this connector is currently
     * connected or not, using supplied icons.
     *
     * @param   onIcon  icon indicating connection
     * @param   offIcon  icon indicating no connection
     * @return  connection indicator
     */
    public JComponent createConnectionIndicator( final Icon onIcon,
                                                 final Icon offIcon ) {
        JLabel label = new JLabel( new Icon() {
            private Icon effIcon() {
                return isConnected() ? onIcon : offIcon;
            }
            public int getIconWidth() {
                return effIcon().getIconWidth();
            }
            public int getIconHeight() {
                return effIcon().getIconHeight();
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                effIcon().paintIcon( c, g, x, y );
            }
        } );
        registerUpdater( label, REPAINT_COMPONENT );
        return label;
    }

    /**
     * Creates a component which indicates whether this connector is currently
     * connected or not, using default icons.
     *
     * @return  connection indicator
     */
    public JComponent createConnectionIndicator() {
        return createConnectionIndicator(
            new ImageIcon( Client.class
                          .getResource( "images/connected-24.gif" ) ),
            new ImageIcon( Client.class
                          .getResource( "images/disconnected-24.gif" ) )
        );
    }

    /**
     * Creates a component which shows an icon for each registered client.
     *
     * @param  vertical  true for vertical box, false for horizontal
     * @param  iconSize  dimension in pixel of each icon (square)
     */
    public JComponent createClientBox( final boolean vertical, int iconSize ) {
        final IconStore iconStore =
            new IconStore( IconStore.createMinimalIcon( iconSize ) );
        IconBox box = new IconBox( iconSize );
        box.setVertical( vertical );
        box.setBorder( createBoxBorder() );
        box.setModel( clientListModel_ );
        box.setRenderer( new IconBox.CellRenderer() {
            public Icon getIcon( IconBox iconBox, Object value, int index ) {
                return IconStore.scaleIcon( iconStore.getIcon( (Client) value ),
                                            iconBox.getTransverseSize(),
                                            2.0, ! vertical );
            }
            public String getToolTipText( IconBox iconBox, Object value,
                                          int index ) {
                return ((Client) value).toString();
            }
        } );
        Dimension boxSize = box.getPreferredSize();
        boxSize.width = 128;
        box.setPreferredSize( boxSize ); 
        registerUpdater( box, ENABLE_COMPONENT );
        return box;
    }

    /**
     * Returns a new component which displays status for this connector.
     *
     * @return   new hub connection monitor component
     */
    public JComponent createMonitorPanel() {
        HubView view = new HubView();
        view.setClientListModel( getClientListModel() );
        view.getClientList().setCellRenderer( createClientListCellRenderer() );
        return view;
    }

    /**
     * Called when the connection status (registered/unregistered) may have
     * changed.  May be called from any thread.
     */
    private void scheduleConnectionChange() {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                boolean isConnected = isConnected();
                if ( isConnected != wasConnected_ ) {
                    wasConnected_ = isConnected;
                    ChangeEvent evt = new ChangeEvent( GuiHubConnector.this );
                    for ( Iterator it = connectionListenerList_.iterator();
                          it.hasNext(); ) {
                        ((ChangeListener) it.next()).stateChanged( evt );
                    }
                }
            }
        } );
    }

    /**
     * Called when the connection status has changed, or may have changed.
     */
    private void updateConnectionState() {
        boolean isConn = isConnected();
        for ( Iterator it = updateMap_.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Object item = entry.getKey();
            ConnectionUpdate update = (ConnectionUpdate) entry.getValue();
            update.setConnected( item, isConn );
        }
    }

    /**
     * Returns a border suitable for icon boxes.
     *
     * @return border
     */
    static Border createBoxBorder() {
        return BorderFactory.createCompoundBorder(
                   new JTextField().getBorder(),
                   BorderFactory.createEmptyBorder( 1, 1, 1, 1 ) );
    }

    /**
     * Adds a given item to the list of objects which will be notified
     * when the hub is connected/disconnected.  By doing it like this
     * rather than with the usual listener mechanism the problem of 
     * retaining references to otherwise unused listeners is circumvented.
     *
     * @param   item   object to be notified
     * @param   updater   object which performs the notification on hub
     *                    connect/disconnect
     */
    void registerUpdater( Object item, ConnectionUpdate updater ) {
        updater.setConnected( item, isConnected() );
        updateMap_.put( item, updater );
    }

    /**
     * Interface defining how an object is to be notified when the hub
     * connection status changes.
     */
    interface ConnectionUpdate {

        /**
         * Invoked when hub connection status changes.
         *
         * @param  item which is being notified
         * @param  isConnected   whether the hub is now connected or not
         */
        void setConnected( Object item, boolean isConnected );
    }

    /**
     * TrackedClientSet implementation used by this class.
     * Implements ListModel as well.
     */
    private static class ListModelTrackedClientSet extends TrackedClientSet
                                                   implements ListModel {

        private final List clientList_;
        private final List listenerList_;

        /**
         * Constructor.
         */
        ListModelTrackedClientSet() {
            clientList_ = new ArrayList();
            listenerList_ = new ArrayList();
        }

        public int getSize() {
            return clientList_.size();
        }

        public Object getElementAt( int index ) {
            return clientList_.get( index );
        }

        public void addListDataListener( ListDataListener listener ) {
            listenerList_.add( listener );
        }

        public void removeListDataListener( ListDataListener listener ) {
            listenerList_.remove( listener );
        }

        public void addClient( final Client client ) {
            super.addClient( client );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    int index = clientList_.size();
                    clientList_.add( client );
                    ListDataEvent evt =
                        new ListDataEvent( ListModelTrackedClientSet.this,
                                           ListDataEvent.INTERVAL_ADDED,
                                           index, index );
                    for ( Iterator it = listenerList_.iterator();
                          it.hasNext(); ) {
                        ((ListDataListener) it.next()).intervalAdded( evt );
                    }
                }
            } );
        }

        public void removeClient( final Client client ) {
            super.removeClient( client );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    int index = clientList_.indexOf( client );
                    assert index >= 0;
                    if ( index >= 0 ) {
                        clientList_.remove( index );
                        ListDataEvent evt =
                            new ListDataEvent( ListModelTrackedClientSet.this,
                                               ListDataEvent.INTERVAL_REMOVED,
                                               index, index );
                        for ( Iterator it = listenerList_.iterator();
                              it.hasNext(); ) {
                            ((ListDataListener) it.next())
                                               .intervalRemoved( evt );
                        }
                    }
                }
            } );
        }

        public void setClients( final Client[] clients ) {
            super.setClients( clients );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    int oldSize = clientList_.size();
                    if ( oldSize > 0 ) {
                        clientList_.clear();
                        ListDataEvent removeEvt =
                            new ListDataEvent( ListModelTrackedClientSet.this,
                                               ListDataEvent.INTERVAL_REMOVED,
                                               0, oldSize - 1); 
                        for ( Iterator it = listenerList_.iterator();
                              it.hasNext(); ) {
                            ((ListDataListener) it.next())
                                               .intervalRemoved( removeEvt );
                        }
                    }
                    if ( clients.length > 0 ) {
                        clientList_.addAll( Arrays.asList( clients ) );
                        int newSize = clientList_.size();
                        ListDataEvent addEvt =
                            new ListDataEvent( ListModelTrackedClientSet.this,
                                               ListDataEvent.INTERVAL_ADDED,
                                               0, newSize - 1); 
                        for ( Iterator it = listenerList_.iterator();
                              it.hasNext(); ) {
                            ((ListDataListener) it.next())
                                               .intervalAdded( addEvt );
                        }
                    }
                }
            } );
        }

        public void updateClient( final Client client,
                                  boolean metaChanged, boolean subsChanged ) {
            super.updateClient( client, metaChanged, subsChanged );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    int index = clientList_.indexOf( client );
                    if ( index >= 0 ) {
                        ListDataEvent evt =
                            new ListDataEvent( ListModelTrackedClientSet.this,
                                               ListDataEvent.CONTENTS_CHANGED,
                                               index, index );
                        for ( Iterator it = listenerList_.iterator();
                              it.hasNext(); ) {
                            ((ListDataListener) it.next())
                                               .contentsChanged( evt );
                        }
                    }
                }
            } );
        }
    }

    /**
     * Action which registers and unregisters with the hub.
     */
    private class RegisterAction extends AbstractAction {

        /**
         * Constructs in an unarmed state.
         */
        public RegisterAction() {
        }

        /**
         * Constructs with a given (initial) sense.
         *
         * @param  active  true to register, false to unregister
         */
        public RegisterAction( boolean active ) {
            this();
            setSense( active );
        }

        /**
         * Sets whether this action registers or unregisters.
         *
         * @param  active  true to register, false to unregister
         */
        public void setSense( boolean active ) {
            putValue( ACTION_COMMAND_KEY, active ? "REGISTER"
                                                 : "UNREGISTER" );
            putValue( NAME, active ? "Register with Hub"
                                   : "Unregister from Hub" );
            putValue( SHORT_DESCRIPTION,
                      active ? "Attempt to connect to SAMP hub"
                             : "Disconnect from SAMP hub" );
        }

        public void actionPerformed( ActionEvent evt ) {
            String cmd = evt.getActionCommand();
            if ( "REGISTER".equals( cmd ) ) {
                setActive( true );
                if ( ! isConnected() ) {
                    registerFailed();
                }
            }
            else if ( "UNREGISTER".equals( cmd ) ) {
                setActive( false );
            }
            else {
                throw new UnsupportedOperationException( "Unknown action "
                                                       + cmd );
            }
        }

        protected void registerFailed() {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Action subclass for popping up a monitor window.
     */
    private class MonitorAction extends AbstractAction {
        private JFrame monitorWindow_;

        /**
         * Constructor.
         */
        MonitorAction() {
            super( "Show Hub Status" );
            putValue( SHORT_DESCRIPTION,
                      "Display a window showing client applications"
                    + " registered with the SAMP hub" );
        }

        public void actionPerformed( ActionEvent evt ) {
            if ( monitorWindow_ == null ) {
                monitorWindow_ = new JFrame( "SAMP Status" );
                monitorWindow_.getContentPane()
                              .add( createMonitorPanel(), BorderLayout.CENTER );
                monitorWindow_.pack();
            }
            monitorWindow_.setVisible( true );
        }
    }

    /**
     * Action subclass for running a hub.
     */
    private class HubAction extends AbstractAction {
        private final boolean external_;
        private final HubMode hubMode_;
        private final boolean isAvailable_;

        /**
         * Constructor.
         *
         * @param   external   false to run in the current JVM,
         *                     true to run in a new one
         * @param   hubMode    hub mode
         */
        HubAction( boolean external, HubMode hubMode ) {
            external_ = external;
            hubMode_ = hubMode;
            putValue( NAME,
                      "Start " + ( external ? "external" : "internal" )
                    + " hub" );
            putValue( SHORT_DESCRIPTION,
                      "Attempts to start up a SAMP hub"
                    + ( external ? " running independently of this application"
                                 : " running within this application" ) );
            setEnabled( ! isConnected() );
            registerUpdater( this, DISABLE_ACTION );
            boolean isAvailable = true;
            if ( external ) {
                try {
                    HubRunner.checkExternalHubAvailability();
                }
                catch ( Exception e ) {
                    isAvailable = false;
                }
            }
            isAvailable_ = isAvailable;
        }

        public void actionPerformed( ActionEvent evt ) {
            try {
                attemptRunHub();
            }
            catch ( Exception e ) {
                ErrorDialog.showError( null, "Hub Start Failed",
                                       e.getMessage(), e );
            }
            setActive( true );
        }

        public boolean isEnabled() {
            return isAvailable_ && super.isEnabled();
        }

        /**
         * Tries to start a hub, but may throw an exception.
         */
        private void attemptRunHub() throws IOException {
            if ( external_ ) {
                HubRunner.runExternalHub( hubMode_ );
            }
            else {
                HubRunner.runHub( hubMode_, XmlRpcKit.getInstance() );
            }
            setActive( true );
        }
    }
}
