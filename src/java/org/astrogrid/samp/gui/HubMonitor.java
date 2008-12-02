package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Client application which uses a {@link GuiHubConnector}
 * to connect to any running hub and display information about all currently
 * registered clients.
 * 
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class HubMonitor extends JPanel {

    private static Logger logger_ =
        Logger.getLogger( HubMonitor.class.getName() );

    /**
     * Constructor.
     *
     * @param  profile  SAMP profile
     * @param  trackMessages  if true, the GUI will contain a visual
     *         representation of messages sent and received
     * @param  autoSec  number of seconds between automatic hub connection
     *         attempts; &lt;=0 means no automatic connections
     */
    public HubMonitor( ClientProfile profile, boolean trackMessages,
                       int autoSec ) {
        super( new BorderLayout() );

        // Set up a new GuiHubConnector and GUI decorations.
        GuiHubConnector connector =
            trackMessages ? new MessageTrackerHubConnector( profile )
                          : new GuiHubConnector( profile );

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
        hubView.getClientList()
               .setCellRenderer( connector.createClientListCellRenderer() );
        add( hubView, BorderLayout.CENTER );

        // Prepare a container for other widgets at the bottom of the window.
        JPanel infoBox = new JPanel( new BorderLayout() );
        add( infoBox, BorderLayout.SOUTH );

        // Create and place components which allow the user to 
        // view and control registration/unregistration explicitly.
        JComponent connectBox = new JPanel( new BorderLayout() );
        connectBox.add( new JButton( connector.getToggleRegisterAction() ),
                        BorderLayout.CENTER );
        connectBox.add( connector.createConnectionIndicator(),
                        BorderLayout.EAST );
        infoBox.add( connectBox, BorderLayout.EAST );

        // Create and place components which provide a compact display 
        // of the connector's status.
        JComponent statusBox = Box.createHorizontalBox();
        statusBox.add( connector.createClientBox( false, 24 ) );
        if ( connector instanceof MessageTrackerHubConnector ) {
            statusBox.add( ((MessageTrackerHubConnector) connector)
                          .createMessageBox( 24 ) );
        }
        infoBox.add( statusBox, BorderLayout.CENTER );

        // Attempt registration, and arrange that if/when unregistered we look
        // for a hub to register with on a regular basis.
        connector.setActive( true );
        connector.setAutoconnect( autoSec );
    }

    /**
     * Does the work for the main method.
     */
    public static int runMain( String[] args ) {
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( HubMonitor.class.getName() )
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [+/-verbose]" )
            .append( " [-xmlrpc internal|apache|xml-log|rpc-log]" )
            .append( "\n           " )
            .append( " [-auto <secs>]" )
            .append( " [-nomsg]" )
            .append( " [-nogui]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        int verbAdjust = 0;
        boolean gui = true;
        boolean trackMsgs = true;
        int autoSec = 3;
        XmlRpcKit xmlrpc = null;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.startsWith( "-auto" ) && it.hasNext() ) {
                it.remove();
                String sauto = (String) it.next();
                it.remove();
                autoSec = Integer.parseInt( sauto );
            }
            else if ( arg.equals( "-gui" ) ) {
                it.remove();
                gui = true;
            }
            else if ( arg.equals( "-nogui" ) ) {
                it.remove();
                gui = false;
            }
            else if ( arg.equals( "-msg" ) ) {
                it.remove();
                trackMsgs = true;
            }
            else if ( arg.equals( "-nomsg" ) ) {
                it.remove();
                trackMsgs = false;
            }
            else if ( arg.equals( "-xmlrpc" ) && it.hasNext() ) {
                it.remove();
                String impl = (String) it.next();
                it.remove();
                try {
                    xmlrpc = XmlRpcKit.getInstanceByName( impl );
                }
                catch ( Exception e ) {
                    logger_.log( Level.INFO, "No XMLRPC implementation " + impl,
                                 e );
                    System.err.println( usage );
                    return 1;
                }
            }
            else if ( arg.startsWith( "-v" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.startsWith( "+v" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.startsWith( "-h" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
            else {
                it.remove();
                System.err.println( usage );
                return 1;
            }
        }
        assert argList.isEmpty();

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Get XML-RPC implementation.
        if ( xmlrpc == null ) {
            xmlrpc = XmlRpcKit.getInstance();
        }

        // Get profile.
        final ClientProfile profile =
            xmlrpc == null ? StandardClientProfile.getInstance()
                           : new StandardClientProfile( xmlrpc );

        // Start the gui in a new window.
        final boolean isVisible = gui;
        final int autoSeconds = autoSec;
        final boolean trackMessages = trackMsgs;
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                JFrame frame = new JFrame( "SAMP HubMonitor" );
                frame.getContentPane()
                     .add( new HubMonitor( profile, trackMessages,
                                           autoSeconds ) );
                frame.setIconImage(
                    new ImageIcon( Metadata.class
                                  .getResource( "images/eye.gif" ) )
                   .getImage() );
                frame.pack();
                frame.setVisible( isVisible );
                frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            }
        } );
        return 0;
    }

    /**
     * Displays a HubMonitor in a window.
     * Use -help flag.
     */
    public static void main( String[] args ) {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }
}
