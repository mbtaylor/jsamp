package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.MessageHandler;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * Client application which uses a {@link GuiHubConnector}
 * to connect to any running hub and display information about all currently
 * registered clients.
 * 
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class HubMonitor extends JPanel {

    private final GuiHubConnector connector_;
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
        connector_ = trackMessages ? new MessageTrackerHubConnector( profile )
                                   : new GuiHubConnector( profile );

        // Declare the default subscriptions.  This is required so that
        // the hub knows the client is subscribed to those hub.event
        // MTypes which inform about client registration, hub shutdown etc.
        connector_.declareSubscriptions( connector_.computeSubscriptions() );

        // Declare metadata about this application.
        Metadata meta = new Metadata();
        meta.setName( "HubMonitor" );
        meta.setDescriptionText( "GUI hub monitor utility" );
        try {
            meta.setIconUrl( UtilServer.getInstance()
                            .exportResource( "/org/astrogrid/samp/images/"
                                           + "eye.gif" )
                            .toString() );
        }
        catch ( IOException e ) {
            logger_.warning( "Can't set icon" );
        }
        meta.put( "author", "Mark Taylor" );
        connector_.declareMetadata( meta );

        // Create and place a component which maintains a display of 
        // currently registered clients.  A more modest GUI could just use
        // connector.getClientListModel() as a model for a JList component.
        add( connector_.createMonitorPanel(), BorderLayout.CENTER );

        // Prepare a container for other widgets at the bottom of the window.
        JPanel infoBox = new JPanel( new BorderLayout() );
        add( infoBox, BorderLayout.SOUTH );

        // Create and place components which allow the user to 
        // view and control registration/unregistration explicitly.
        JComponent connectBox = new JPanel( new BorderLayout() );
        connectBox.add( new JButton( connector_.createToggleRegisterAction() ),
                        BorderLayout.CENTER );
        connectBox.add( connector_.createConnectionIndicator(),
                        BorderLayout.EAST );
        infoBox.add( connectBox, BorderLayout.EAST );

        // Create and place components which provide a compact display 
        // of the connector's status.
        JComponent statusBox = Box.createHorizontalBox();
        statusBox.add( connector_.createClientBox( false, 24 ) );
        if ( connector_ instanceof MessageTrackerHubConnector ) {
            statusBox.add( ((MessageTrackerHubConnector) connector_)
                          .createMessageBox( 24 ) );
        }
        infoBox.add( statusBox, BorderLayout.CENTER );

        // Attempt registration, and arrange that if/when unregistered we look
        // for a hub to register with on a regular basis.
        connector_.setActive( true );
        connector_.setAutoconnect( autoSec );
    }

    /**
     * Returns this monitor's HubConnector.
     *
     * @return  hub connector
     */
    public GuiHubConnector getHubConnector() {
        return connector_;
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
            .append( "\n           " )
            .append( " [-auto <secs>]" )
            .append( " [-nomsg]" )
            .append( " [-nogui]" )
            .append( "\n           " )
            .append( " [-mtype <pattern>]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        int verbAdjust = 0;
        boolean gui = true;
        boolean trackMsgs = true;
        int autoSec = 3;
        Subscriptions subs = new Subscriptions();
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
            else if ( arg.startsWith( "-mtype" ) && it.hasNext() ) {
                it.remove();
                String mpat = (String) it.next();
                it.remove();
                subs.addMType( mpat );
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

        // Get profile.
        final ClientProfile profile =DefaultClientProfile.getProfile();

        // Create the HubMonitor.
        final HubMonitor monitor =
            new HubMonitor( profile, trackMsgs, autoSec );

        // Add a handler for extra MTypes if so requested.
        if ( ! subs.isEmpty() ) {
            final Subscriptions extraSubs = subs;
            HubConnector connector = monitor.getHubConnector();
            final Response dummyResponse = new Response();
            dummyResponse.setStatus( Response.WARNING_STATUS );
            dummyResponse.setResult( new HashMap() );
            dummyResponse.setErrInfo( new ErrInfo( "Message logged, "
                                                 + "no other action taken" ) );
            connector.addMessageHandler( new MessageHandler() {
                public Map getSubscriptions() {
                    return extraSubs;
                }
                public void receiveNotification( HubConnection connection,
                                                 String senderId,
                                                 Message msg ) {
                }
                public void receiveCall( HubConnection connection,
                                         String senderId, String msgId,
                                         Message msg )
                        throws SampException {
                    connection.reply( msgId, dummyResponse );
                }
            } );
            connector.declareSubscriptions( connector.computeSubscriptions() );
        }

        // Start the gui in a new window.
        final boolean isVisible = gui;
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                JFrame frame = new JFrame( "SAMP HubMonitor" );
                frame.getContentPane().add( monitor );
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
