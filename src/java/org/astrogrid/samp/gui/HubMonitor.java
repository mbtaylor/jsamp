package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.client.HubConnector;

/**
 * Client application which uses a 
 * {@link org.astrogrid.samp.client.HubConnector}
 * to connect to any running hub and display information about all currently
 * registered clients.
 * 
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class HubMonitor extends JPanel {

    /**
     * Constructor.
     *
     * @param  autoSec  number of seconds between automatic hub connection
     *         attempts; &lt;=0 means no automatic connections
     */
    public HubMonitor( int autoSec ) throws SampException {
        super( new BorderLayout() );

        // Set up a new HubConnector.
        HubConnector connector = new HubConnector();

        // Declare the default subscriptions.  This is required so that
        // the hub knows the client is subscribed to those hub.event
        // MTypes which inform about client registration, hub shutdown etc.
        connector.declareSubscriptions( connector.computeSubscriptions() );

        // Declare metadata about this application.
        Metadata meta = new Metadata();
        meta.setName( "HubMonitor" );
        meta.setDescriptionText( "GUI hub monitor utility" );
        meta.setIconUrl( "http://www.star.bristol.ac.uk/~mbt/"
                       + "plastic/images/eye.gif" );
        meta.put( "author", "Mark Taylor" );
        connector.declareMetadata( meta );

        // Create and place a component which maintains a display of 
        // currently registered clients.  A more modest GUI could just
        // use the client list model as a model for a JList component.
        HubView hubView = new HubView();
        hubView.setClientListModel( connector.getClientListModel() );
        add( hubView, BorderLayout.CENTER );

        // Prepare a container for other widgets at the bottom of the window.
        JPanel connectBox = new JPanel( new BorderLayout() );
        add( connectBox, BorderLayout.SOUTH );

        // Create and place a component which allows the user to control
        // registration/unregistration explicitly.
        connectBox.add( new JButton( connector.getRegisterAction() ),
                        BorderLayout.CENTER );

        // Create and place a component which indicates current registration
        // status of this client.
        connectBox.add( connector.createConnectionIndicator(),
                        BorderLayout.EAST );

        // Attempt registration, and arrange that if/when unregistered we look
        // for a hub to register with on a regular basis.
        connector.setActive( true );
        connector.setAutoconnect( autoSec );
    }

    /**
     * Displays a HubMonitor in a window.
     */
    public static void main( String[] args ) throws SampException {
        JFrame frame = new JFrame( "SAMP HubMonitor" );
        frame.getContentPane().add( new HubMonitor( 2 ) );
        frame.pack();
        frame.setVisible( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    }
}
