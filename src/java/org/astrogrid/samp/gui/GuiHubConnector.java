package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.SampUtils;
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
    private final RegisterAction toggleRegAct_;
    private final RegisterAction regAct_;
    private final RegisterAction unregAct_;
    private final Action monitorAct_;
    private final Collection connectionComponentSet_;
    private final Map hubActMap_;
    private boolean wasConnected_;

    /**
     * Constructs a hub connector based on a given profile instance.
     *
     * @param  profile  profile implementation
     */
    public GuiHubConnector( ClientProfile profile ) {
        super( profile, new ListModelTrackedClientSet() );
        clientListModel_ = (ListModelTrackedClientSet) getClientSet();
        connectionListenerList_ = new ArrayList();

        // Set up actions and other items to be updated with connection state.
        regAct_ = new RegisterAction( true );
        unregAct_ = new RegisterAction( false );
        toggleRegAct_ = new RegisterAction();
        monitorAct_ = new MonitorAction();
        connectionComponentSet_ = new WeakSet();
        hubActMap_ = new HashMap();

        // Update state when hub connection starts/stops.
        addConnectionListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateConnectionState();
            }
        } );
        updateConnectionState();
    }

    public HubConnection getConnection() throws SampException {
        HubConnection connection = super.getConnection();
        scheduleConnectionChange();
        return connection;
    }

    protected void disconnect() {
        super.disconnect();
        scheduleConnectionChange();
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
    public Action getRegisterAction() {
        return regAct_;
    }

    /**
     * Returns an action which attempts to unregister from the hub.
     * Disabled when already unregistered.
     *
     * @return  unregistration action
     */
    public Action getUnregisterAction() {
        return unregAct_;
    }

    /**
     * Returns an action which toggles hub registration.
     *
     * @return   registration toggler action
     */
    public Action getToggleRegisterAction() {
        return toggleRegAct_;
    }

    /**
     * Returns an action which will display a SAMP hub monitor window.
     *
     * @return   monitor window action
     */
    public Action getShowMonitorAction() {
        return monitorAct_;
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
    public Action getHubAction( boolean external, HubMode hubMode ) {
        Object key = Arrays.asList( new Object[] { new Boolean( external ),
                                                   hubMode } );
        if ( ! hubActMap_.containsKey( key ) ) {
            hubActMap_.put( key, new HubAction( external, hubMode ) );
        }
        return (HubAction) hubActMap_.get( key );
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
        connectionComponentSet_.add( label );
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
    public JComponent createClientBox( boolean vertical, int iconSize ) {
        final IconStore iconStore =
            new IconStore( IconStore.createMinimalIcon( iconSize ) );
        IconBox box = new IconBox( iconSize );
        box.setVertical( vertical );
        box.setBorder( createBoxBorder() );
        box.setModel( clientListModel_ );
        box.setRenderer( new IconBox.CellRenderer() {
            public Icon getIcon( IconBox iconBox, Object value, int index ) {
                return IconStore.sizeIcon( iconStore.getIcon( (Client) value ),
                                           iconBox.getTransverseSize() );
            }
            public String getToolTipText( IconBox iconBox, Object value,
                                          int index ) {
                return SampUtils.toString( (Client) value );
            }
        } );
        Dimension boxSize = box.getPreferredSize();
        boxSize.width = 128;
        box.setPreferredSize( boxSize ); 
        return box;
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
     * Returns a border suitable for icon boxes.
     *
     * @return border
     */
    Border createBoxBorder() {
        return BorderFactory.createCompoundBorder(
                   BorderFactory.createLineBorder( Color.BLACK ),
                   BorderFactory.createLineBorder( Color.WHITE, 2 ) );
    }

    /**
     * Called when the connection status has changed, or may have changed.
     */
    private void updateConnectionState() {
        boolean isConn = isConnected();
        regAct_.setEnabled( ! isConn );
        unregAct_.setEnabled( isConn );
        toggleRegAct_.setSense( ! isConn );
        for ( Iterator it = hubActMap_.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            HubAction hubAct = (HubAction) entry.getValue();
            hubAct.setEnabled( ! isConn );
        }
        for ( Iterator it = connectionComponentSet_.iterator();
              it.hasNext(); ) {
            ((Component) it.next()).repaint();
        }
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

        public void updateClient( final Client client ) {
            super.updateClient( client );
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
                    Toolkit.getDefaultToolkit().beep();
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
                HubView view = new HubView();
                view.setClientListModel( getClientListModel() );
                monitorWindow_ = new JFrame( "SAMP Clients" );
                monitorWindow_.getContentPane()
                              .add( view, BorderLayout.CENTER );
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

    /**
     * Set in which elements are weakly linked.  Elements only weakly
     * reachable may disappear.  Based on a WeakHashMap.
     */
    private static class WeakSet extends AbstractSet {
        private final Map map_;

        /**
         * Constructor.
         */
        WeakSet() {
            map_ = new WeakHashMap();
        }

        public int size() {
            return map_.size();
        }

        public Iterator iterator() {
            return map_.keySet().iterator();
        }

        public boolean add( Object obj ) {
            return map_.put( obj, null ) == null;
        }
    }
}
