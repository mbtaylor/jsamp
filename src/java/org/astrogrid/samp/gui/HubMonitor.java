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
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.xmlrpc.XmlRpcImplementation;

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

    private static Logger logger_ =
        Logger.getLogger( HubMonitor.class.getName() );

    /**
     * Constructor.
     *
     * @param  profile  SAMP profile
     * @param  autoSec  number of seconds between automatic hub connection
     *         attempts; &lt;=0 means no automatic connections
     */
    public HubMonitor( ClientProfile profile, int autoSec ) {
        super( new BorderLayout() );

        // Set up a new HubConnector.
        HubConnector connector = new HubConnector( profile );

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
            .append( " [-xmlrpc apache|internal]" )
            .append( "\n           " )
            .append( " [-auto <secs>]" )
            .append( " [-nogui]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        int verbAdjust = 0;
        boolean gui = true;
        int autoSec = 3;
        XmlRpcImplementation xmlrpc = null;
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
            else if ( arg.equals( "-xmlrpc" ) && it.hasNext() ) {
                it.remove();
                String impl = (String) it.next();
                it.remove();
                try {
                    xmlrpc = XmlRpcImplementation.getInstanceByName( impl );
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
        if ( xmlrpc == null ) {
            xmlrpc = XmlRpcImplementation.getInstance();
        }

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Get profile.
        ClientProfile profile =
            xmlrpc == null
                ? StandardClientProfile.getInstance()
                : new StandardClientProfile( xmlrpc.getClient(),
                                             xmlrpc.getServerFactory() );

        // Start the gui in a new window.
        JFrame frame = new JFrame( "SAMP HubMonitor" );
        frame.getContentPane().add( new HubMonitor( profile, autoSec ) );
        frame.setIconImage( new ImageIcon( Metadata.class
                                          .getResource( "images/eye.gif" ) )
                           .getImage() );
        frame.pack();
        frame.setVisible( gui );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
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
