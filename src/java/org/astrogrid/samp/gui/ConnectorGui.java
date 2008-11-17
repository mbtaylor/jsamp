package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JFrame;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.xmlrpc.HubRunner;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Provides a number of useful Swing actions and components for use
 * with a {@link org.astrogrid.samp.client.HubConnector} instance.
 * Many of these can be lifted direct to populate application menus etc.
 *
 * @author   Mark Taylor
 * @since    2 Sep 2008
 */
public class ConnectorGui {

    private final HubConnector connector_;
    private final ListModel clientListModel_;
    private final RegisterAction toggleRegAct_;
    private final RegisterAction regAct_;
    private final RegisterAction unregAct_;
    private final Action monitorAct_;
    private final Action intHubAct_;
    private final Action extHubAct_;
    private final Collection clientComponentSet_;
    private final Collection connectionComponentSet_;

    /**
     * Constructor.
     *
     * @param   connector  hub connector on which this object depends
     */
    public ConnectorGui( HubConnector connector ) {
        connector_ = connector;
        regAct_ = new RegisterAction( true );
        unregAct_ = new RegisterAction( false );
        toggleRegAct_ = new RegisterAction();
        monitorAct_ = new MonitorAction();
        intHubAct_ = new HubAction( false, false );
        extHubAct_ = new HubAction( true, true );
        connectionComponentSet_ = new WeakSet();
        clientComponentSet_ = new WeakSet();

        // Update state when hub connection starts/stops.
        connector.addConnectionListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateConnectionState();
            }
        } );
        updateConnectionState();

        // Update state when other clients register/unregister.
        clientListModel_ = connector.getClientListModel();
        clientListModel_.addListDataListener( new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                updateClients();
            }
            public void intervalAdded( ListDataEvent evt ) {
                updateClients();
            }
            public void intervalRemoved( ListDataEvent evt ) {
                updateClients();
            }
        } );
        updateClients();
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
     * Returns an action which will start up a SAMP hub in a new independent
     * JVM.
     *
     * @return  external hub action
     */
    public Action getExternalHubAction() {
        return extHubAct_;
    }

    /**
     * Returns an action which will start up a SAMP hub in the current JVM.
     *
     * @return   internal hub action
     */
    public Action getInternalHubAction() {
        return intHubAct_;
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
                return connector_.isConnected() ? onIcon : offIcon;
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
     * @param  nIcon     number of icons there is size for in the box 
     *                   (affects only component minimum/preferred size)
     */
    public JComponent createClientBox( boolean vertical, int iconSize,
                                       int nIcon) {
        IconBox box = new IconBox( vertical, iconSize );
        final IconStore iconStore =
            new IconStore( iconSize, IconStore.createMinimalIcon( iconSize ) );
        List entryList = new AbstractList() {
            public int size() {
                return clientListModel_.getSize();
            }
            public Object get( int index ) {
                final Client client =
                    (Client) clientListModel_.getElementAt( index );
                return new IconBox.Entry() {
                    public Icon getIcon() {
                        return iconStore.getIcon( client );
                    }
                    public String getToolTipText() {
                        return SampUtils.toString( client );
                    }
                };
            }
        };
        box.setEntryList( entryList );
        box.setPreferredSize( box.getSizeForSlots( nIcon ) );
        clientComponentSet_.add( box );
        return box;
    }

    /**
     * Called when the connection status has changed, or may have changed.
     */
    private void updateConnectionState() {
        boolean isConn = connector_.isConnected();
        regAct_.setEnabled( ! isConn );
        unregAct_.setEnabled( isConn );
        toggleRegAct_.setSense( ! isConn );
        intHubAct_.setEnabled( ! isConn );
        extHubAct_.setEnabled( ! isConn );
        for ( Iterator it = connectionComponentSet_.iterator();
              it.hasNext(); ) {
            ((Component) it.next()).repaint();
        }
        for ( Iterator it = clientComponentSet_.iterator(); it.hasNext(); ) {
            ((Component) it.next()).setEnabled( isConn );
        }
    }

    /**
     * Called when the client list has changed.
     */
    private void updateClients() {
        for ( Iterator it = clientComponentSet_.iterator(); it.hasNext(); ) {
            ((Component) it.next()).repaint();
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
                connector_.setActive( true );
                if ( ! connector_.isConnected() ) {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
            else if ( "UNREGISTER".equals( cmd ) ) {
                connector_.setActive( false );
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
                view.setClientListModel( connector_.getClientListModel() );
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
        private final boolean gui_;

        /**
         * Constructor.
         *
         * @param   external   false to run in the current JVM,
         *                     true to run in a new one
         * @param   gui   true to show a hub monitor window
         */
        HubAction( boolean external, boolean gui ) {
            external_ = external;
            gui_ = gui;
            putValue( NAME,
                      "Start " + ( external ? "external" : "internal" )
                    + " hub" );
            putValue( SHORT_DESCRIPTION,
                      "Attempts to start up a SAMP hub"
                    + ( external ? " running independently of this application"
                                 : " running within this application" ) );
        }

        public void actionPerformed( ActionEvent evt ) {
            try {
                attemptRunHub();
            }
            catch ( Exception e ) {
                ErrorDialog.showError( null, "Hub Start Failed",
                                       e.getMessage(), e );
            }
            connector_.setActive( true );
        }

        /**
         * Tries to start a hub, but may throw an exception.
         */
        private void attemptRunHub() throws IOException {
            if ( external_ ) {
                HubRunner.runExternalHub( gui_ );
            }
            else {
                HubRunner.runHub( gui_, XmlRpcKit.getInstance() );
            }
            connector_.setActive( true );
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
