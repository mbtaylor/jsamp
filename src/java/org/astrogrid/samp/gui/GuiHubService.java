package org.astrogrid.samp.gui;

import java.util.Map;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JFrame;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;
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
}
