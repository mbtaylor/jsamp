package org.astrogrid.samp.gui;

import java.util.Map;
import java.util.Random;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.ClientSet;
import org.astrogrid.samp.hub.HubClient;

/**
 * BasicHubService subclass which provides a GUI window displaying hub
 * status as well as the basic hub services.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class GuiHubService extends BasicHubService {

    private GuiClientSet clientSet_;
    private ListSelectionModel selectionModel_;

    /**
     * Constructor.
     *
     * @param  random  random number generator used for message tags etc
     */
    public GuiHubService( Random random ) {
        super( random );
    }

    public void start() {
        super.start();
        clientSet_ = (GuiClientSet) getClientSet();
    }

    protected ClientSet createClientSet() {
        return new GuiClientSet( getIdComparator() );
    }

    /**
     * Creates a new component containing a display of the current hub
     * internal state.
     *
     * @return   new hub viewer panel
     */
    public JComponent createHubPanel() {
        HubView hubView = new HubView( true );
        hubView.setClientListModel( getClientListModel() );
        JList jlist = hubView.getClientList();
        jlist.setCellRenderer( new ClientListCellRenderer() );
        jlist.addMouseListener( new HubClientPopupListener( this ) );
        selectionModel_ = jlist.getSelectionModel();
        return hubView;
    }

    /**
     * Creates a new window which maintains a display of the current hub
     * internal state.
     *
     * @return   new hub viewer window
     */
    public JFrame createHubWindow() {
        JFrame frame = new JFrame( "SAMP Hub" );
        frame.getContentPane().add( createHubPanel() );
        frame.setIconImage( new ImageIcon( Client.class
                                          .getResource( "images/hub.png" ) )
                           .getImage() );
        frame.pack();
        return frame;
    }

    protected void declareMetadata( HubClient caller, Map meta )
            throws SampException {
        super.declareMetadata( caller, meta );
        clientSet_.scheduleClientChanged( caller );
    }

    protected void declareSubscriptions( HubClient caller, Map subscriptions )
            throws SampException {
        super.declareSubscriptions( caller, subscriptions );
        clientSet_.scheduleClientChanged( caller );
    }

    /**
     * Returns a ListModel containing information about clients currently 
     * registered with this hub.
     *
     * @return   list model in which each element is a 
     *           {@link org.astrogrid.samp.Client}
     */
    public ListModel getClientListModel() {
        return clientSet_;
    }

    /**
     * Returns the selection model corresponding to this service's client
     * list model.
     *
     * @return   list selection model for client selection
     */
    public ListSelectionModel getClientSelectionModel() {
        return selectionModel_;
    }

    /**
     * Returns the client object currently selected in the GUI, if any.
     *
     * @return  currently selected client, or null
     */
    private Client getSelectedClient() {
        ListSelectionModel selModel = getClientSelectionModel();
        int isel = selModel.getMinSelectionIndex();
        Object selected = isel >= 0 ? getClientListModel().getElementAt( isel )
                                    : null;
        return selected instanceof Client ? (Client) selected : null;
    }

    /**
     * Returns an array of menus which may be added to a window
     * containing this service's window.
     *
     * @return  menu array
     */
    public JMenu[] createMenus() {
        final HubConnection serviceConnection = getServiceConnection();
        final String hubId = serviceConnection.getRegInfo().getSelfId();

        /* Broadcast ping action. */
        final Message pingMessage = new Message( "samp.app.ping" );
        final Action pingAllAction = new AbstractAction( "Ping all" ) {
            public void actionPerformed( ActionEvent evt ) {
                new SampThread( evt, "Ping Error", "Error broadcasting ping" ) {
                    protected void sampRun() throws SampException {
                        serviceConnection.callAll( "ping-tag", pingMessage );
                    }
                }.start();
            }
        };
        pingAllAction.putValue( Action.SHORT_DESCRIPTION,
                                "Send ping message to all clients" );

        /* Single client ping action. */
        final String pingSelectedName = "Ping selected client";
        final Action pingSelectedAction =
                new AbstractAction( pingSelectedName ) {
            public void actionPerformed( ActionEvent evt ) {
                final Client client = getSelectedClient();
                if ( client != null ) {
                    new SampThread( evt, "Ping Error",
                                    "Error sending ping to " + client ) {
                        protected void sampRun() throws SampException {
                            serviceConnection.call( client.getId(), "ping-tag",
                                                    pingMessage );
                        }
                    }.start();
                }
            }
        };
        pingSelectedAction.putValue( Action.SHORT_DESCRIPTION,
                                     "Send ping message to selected client" );

        /* Single client disconnect action. */
        final String disconnectSelectedName = "Disconnect selected client";
        final Action disconnectSelectedAction =
                new AbstractAction( disconnectSelectedName ) {
            public void actionPerformed( ActionEvent evt ) {
                final Client client = getSelectedClient();
                if ( client != null ) {
                    new SampThread( evt, "Disconnect Error",
                                    "Error disconnecting " + client ) {
                        protected void sampRun() throws SampException {
                            disconnect( client.getId(),
                                        "GUI hub user requested ejection" );
                        }
                    }.start();
                }
            }
        };
        disconnectSelectedAction.putValue( Action.SHORT_DESCRIPTION,
                                           "Forcibly disconnect selected client"
                                         + " from the hub" );

        /* Ensure that actions are kept up to date. */
        ListSelectionListener selListener = new ListSelectionListener() {
            public void valueChanged( ListSelectionEvent evt ) {
                Client client = getSelectedClient();
                boolean isSel = client != null;
                boolean canPing = isSel
                               && client.getSubscriptions()
                                        .isSubscribed( pingMessage.getMType() );
                boolean canDisco = isSel
                               && ! hubId.equals( client.getId() );
                pingSelectedAction.setEnabled( canPing );
                disconnectSelectedAction.setEnabled( canDisco );
                String clientDesignation = client == null
                                         ? ""
                                         : ( " (" + client + ")" );
                pingSelectedAction.putValue( Action.NAME,
                                             pingSelectedName
                                             + clientDesignation );
                disconnectSelectedAction.putValue( Action.NAME,
                                                   disconnectSelectedName
                                                   + clientDesignation );
            }
        };
        getClientSelectionModel().addListSelectionListener( selListener );
        selListener.valueChanged( null );

        /* Prepare and return menus containing the actions. */
        JMenu clientMenu = new JMenu( "Clients" );
        clientMenu.setMnemonic( KeyEvent.VK_C );
        clientMenu.add( new JMenuItem( pingAllAction ) );
        clientMenu.add( new JMenuItem( pingSelectedAction ) );
        clientMenu.add( new JMenuItem( disconnectSelectedAction ) );
        return new JMenu[] { clientMenu };
    }
}
