package org.astrogrid.samp.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.ResultHandler;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.gui.AbstractCallActionManager;
import org.astrogrid.samp.gui.SendActionManager;
import org.astrogrid.samp.gui.UniformCallActionManager;
import org.astrogrid.samp.gui.GuiHubConnector;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * Dialog window for sending a fixed load-type message to a selected
 * client or clients.
 * This is intended for use as a small free-standing SAMP client that
 * for instance can be used as a browser helper application.
 *
 * <p>A main method is supplied for command-line use.
 *
 * @author   Mark Taylor
 * @since    21 Aug 2019
 */
public class SampLoad extends JDialog {

    private final GuiHubConnector connector_;
    private final URL url_;
    private final String mtype_;
    private final JComboBox targetSelector_;
    private final JLabel statusField_;
    private final Action sendAct_;
    private Map responseMap_;

    private static final URL ICON_URL =
        SampLoad.class.getResource( "/org/astrogrid/samp/images/sampload.png" );
    private static final Logger logger_ =
        Logger.getLogger( SampLoad.class.getName() );

    /**
     * Constructor.
     *
     * @param  connector   connector
     * @param  rtype   resource type
     * @param  URL     resource URL
     * @param  sendType   type of item being sent (for user info)
     * @param  location   location of item being sent (for user info)
     */
    public SampLoad( GuiHubConnector connector, final ResourceType rtype,
                     final URL url, String location ) {
        super( (JDialog) null, "SAMP Loader", true );
        connector_ = connector;
        url_ = url;
        mtype_ = rtype == null ? null : rtype.getMType();

        // Prepare to send the message to selected target client(s).
        String tname = rtype == null ? null : rtype.getName();
        AbstractCallActionManager callManager =
                new UniformCallActionManager( this, connector, mtype_, tname ) {
            protected Map createMessage() {
                return rtype.createMessage( url );
            }
            protected ResultHandler createResultHandler( HubConnection conn,
                                                         Message msg,
                                                         Client[] recipients ) {
                final Collection recips =
                    new HashSet( Arrays.asList( recipients ) );
                return new ResultHandler() {

                    // Record which responses we have got.
                    public void result( Client responder, Response response ) {
                        responseMap_.put( responder, response );
                        if ( response.isOK() ) {
                            recips.remove( responder );
                        }
                        updateStatus();
                    }

                    // If all successful, just dismiss the window after
                    // a short delay.  If there were any problems,
                    // leave it posted so that any error messages are visible.
                    public void done() {
                        boolean success = recips.size() == 0;
                        if ( success ) {
                            Timer timer =
                                    new Timer( 1000, new ActionListener() {
                                public void actionPerformed( ActionEvent evt ) {
                                    closeDialog();
                                }
                            } );
                            timer.setRepeats( false );
                            timer.start();
                        }
                    }
                };
            }
        };

        // Provide a target client selection widget and associated send button.
        final ComboBoxModel targetModel;
        if ( rtype == null ) {
            targetModel =
                new DefaultComboBoxModel( new String[] { SendActionManager
                                                        .BROADCAST_TARGET } );
            sendAct_ = new AbstractAction( "Send to selected target" ) {
                public void actionPerformed( ActionEvent evt ) {
                }
            };
        }
        else {
            targetModel = callManager.createTargetSelector();
            sendAct_ = callManager.createTargetAction( targetModel );
        }
        targetSelector_ = new JComboBox( targetModel ) {
            public Dimension getMaximumSize() {
                return super.getPreferredSize();
            }
        };

        // Provide a button that dismisses the window.
        Action cancelAct = new AbstractAction( "Cancel" ) {
            public void actionPerformed( ActionEvent evt ) {
                closeDialog();
            }
        };
        setDefaultCloseOperation( DISPOSE_ON_CLOSE );

        // Update GUI on relevant SAMP events.
        connector_.addConnectionListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateStatus();
            }
        } );
        targetModel.addListDataListener( new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                updateSelector();
            }
            public void intervalAdded( ListDataEvent evt ) {
                updateSelector();
            }
            public void intervalRemoved( ListDataEvent evt ) {
                updateSelector();
            }
        } );

        // We don't have hub autodetect switched on, since in most cases
        // this will not be a long-lived application, but if the user
        // interacts with the window, take it as an opportunity
        // to see if a hub has started.
        getContentPane().addMouseListener( new MouseAdapter() {
            public void mouseEntered( MouseEvent evt ) {
                checkConnection();
            }
            public void mouseExited( MouseEvent evt ) {
                checkConnection();
            }
            public void mousePressed( MouseEvent evt ) {
                checkConnection();
            }
            private void checkConnection() {
                try {
                    connector_.getConnection();
                }
                catch ( SampException e ) {
                }
            }
        } );

        // Note when a send has started by resetting the response map to null.
        JButton sendButton = new JButton( sendAct_ );
        sendButton.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                responseMap_ = new LinkedHashMap();
                updateStatus();
            }
        } );

        // Place components.
        statusField_ = new JLabel();

        JComponent displayPanel = new JPanel( new GridBagLayout() );
        GridBagConstraints gbc = new GridBagConstraints();
        addLine( displayPanel, gbc, "Location", createField( location ), true );
        addLine( displayPanel, gbc, "MType", createField( mtype_ ), true );
        addLine( displayPanel, gbc, "Target", targetSelector_, false );
        addLine( displayPanel, gbc, "Status", statusField_, true );

        JComponent buttLine = Box.createHorizontalBox(); 
        buttLine.add( Box.createHorizontalGlue() );
        buttLine.add( sendButton );
        buttLine.add( Box.createHorizontalStrut( 10 ) );
        buttLine.add( new JButton( cancelAct ) );
        buttLine.add( Box.createHorizontalGlue() );
        buttLine.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );

        Box qbox = Box.createVerticalBox();
        Icon qicon = ICON_URL != null
                   ? new ImageIcon( ICON_URL )
                   : UIManager.getIcon( "OptionPane.questionIcon" );
        qbox.add( new JLabel( qicon ) );
        qbox.add( Box.createVerticalGlue() );
        qbox.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );

        Component hstrut = Box.createHorizontalStrut( 450 );

        JComponent contentPane = (JComponent) getContentPane();
        contentPane.setLayout( new BorderLayout() );
        contentPane.add( displayPanel, BorderLayout.CENTER );
        contentPane.add( buttLine, BorderLayout.SOUTH );
        contentPane.add( qbox, BorderLayout.WEST );
        contentPane.add( hstrut, BorderLayout.NORTH );
        contentPane.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );

        // Initialise state.
        updateSelector();
    }

    /**
     * Updates the GUI according to current state.
     */
    private void updateStatus() {
        final String status;
        final boolean canSend;
        if ( url_ == null ) {
            status = "No such file";
            canSend = false;
        }
        else if ( mtype_ == null ) {
            status = "Unknown resource type (not "
                   + commaJoin( ResourceType.getKnownResourceTypes() ) + ")";
            canSend = false;
        }
        else if ( ! connector_.isConnected() ) {
            status = "No hub";
            canSend = false;
        }
        else if ( targetSelector_.getItemCount() <= 1 ) {
            status = "No clients for " + mtype_;
            canSend = false;
        }
        else if ( responseMap_ == null ) {
            status = "Ready";
            canSend = true;
        }
        else if ( responseMap_.size() == 0 ) {
            status = "sending ...";
            canSend = false;
        }
        else {
            StringBuffer sbuf = new StringBuffer();
            for ( Iterator it = responseMap_.entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                if ( sbuf.length() > 0 ) {
                    sbuf.append( "; " );
                }
                Client client = (Client) entry.getKey();
                Response response = (Response) entry.getValue();
                sbuf.append( client + ": " ); 
                if ( response.isOK() ) {
                    sbuf.append( "OK" );
                }
                else {
                    ErrInfo err = response.getErrInfo();
                    if ( err == null ) {
                        sbuf.append( "ERROR" );
                    }
                    else {
                        sbuf.append( response.getStatus() )
                            .append( " " )
                            .append( err.getErrortxt() );
                    }
                }
            }
            status = sbuf.toString();
            canSend = false;
        }
        statusField_.setText( status );
        sendAct_.setEnabled( canSend );
    }

    /**
     * Updates the target client selector according to the current SAMP
     * client list.
     */
    private void updateSelector() {

        // Note, the first item in the list corresponds to Broadcast
        // (all clients), and subsequent items are single clients.
        if ( targetSelector_.getItemCount() == 2 ) {
            targetSelector_.setSelectedIndex( 1 );
        }
        else if ( targetSelector_.getSelectedIndex() < 0 ) {
            targetSelector_.setSelectedIndex( 0 );
        }
        updateStatus();
    }

    /**
     * Closes and disposes this dialogue.
     */
    private void closeDialog() {
        setVisible( false );
        dispose();
    }

    /**
     * Utility method to append a labelled component to a JComponent
     * using GridBagLayout.
     *
     * @param  panel  container
     * @param  gbc    constraints object
     * @param  labelTxt   text of label
     * @param  comp   component to add
     * @param  hfill  true to fill horizontal line
     */
    private static void addLine( JComponent panel, GridBagConstraints gbc,
                                 String labelTxt, JComponent comp,
                                 boolean hfill ) {
        GridBagLayout layer = (GridBagLayout) panel.getLayout();
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.0;
        gbc.insets = new Insets( 2, 5, 5, 2 );
        JLabel label = new JLabel( labelTxt + ": " );
        layer.setConstraints( label, gbc );
        panel.add( label );
        GridBagConstraints gbc1 = (GridBagConstraints) gbc.clone();
        gbc1.gridx = 1;
        gbc1.anchor = GridBagConstraints.WEST;
        gbc1.weightx = 1.0;
        gbc1.fill = hfill ? GridBagConstraints.HORIZONTAL
                          : GridBagConstraints.NONE;
        gbc1.gridwidth = GridBagConstraints.REMAINDER;
        layer.setConstraints( comp, gbc1 );
        panel.add( comp );
    }

    /**
     * Utility method to create an uneditable field with given text.
     *
     * @param  txt  text content
     * @return  field component
     */
    private static JTextField createField( String txt ) {
        JTextField field = new JTextField();
        field.setEditable( false );
        field.setText( txt );
        field.setCaretPosition( 0 );
        return field;
    }

    /**
     * Returns a comma-separated string joining the toString values of
     * the elements of a supplied array.
     *
     * @param  items   array of items
     * @return  "i1, i2, ..., iN"
     */
    private static String commaJoin( Object[] items ) {
        StringBuffer sbuf = new StringBuffer();
        for ( int i = 0; i < items.length; i++ ) {
            if ( sbuf.length() > 0 ) {
                sbuf.append( ", " );
            }
            sbuf.append( String.valueOf( items[ i ] ) );
        }
        return sbuf.toString();
    }

    /**
     * Tries to turn a string into a URL.
     *
     * @param  location  URL or filename
     * @return  URL, or null
     */
    private static final URL getUrl( String location ) {
        File f = new File( location );
        if ( f.exists() ) {
            return SampUtils.fileToUrl( f );
        }
        try {
            return new URL( location );
        }
        catch ( MalformedURLException e ) {
        }
        return null;
    }

    /**
     * Main invocation method.
     * Use -help for help.
     *
     * @param  args   arg vector
     */
    public static int runMain( String[] args ) throws IOException {
        ResourceType[] resourceTypes = ResourceType.getKnownResourceTypes();

        // Assemble usage string.
        StringBuffer rtbuf = new StringBuffer();
        for ( int ir = 0; ir < resourceTypes.length; ir++ ) {
            if ( rtbuf.length() > 0 ) {
                rtbuf.append( "|" );
            }
            rtbuf.append( resourceTypes[ ir ].getName().toLowerCase() );
        }
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " + SampLoad.class.getName() )
            .append( "\n         " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n         " )
            .append( " [-rtype " + rtbuf + "]" )
            .append( " <file-or-url>" )
            .append( "\n" )
            .toString();

        // Set up variables which can be set or changed by the argument list.
        ResourceType rtype = null;
        String location = null;
        int verbAdjust = 0;

        // Parse the argument list.
        List argList = new ArrayList( Arrays.asList( args ) );
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-rtype" ) && rtype == null && it.hasNext() ) {
                it.remove();
                String rtName = (String) it.next();
                it.remove();
                for ( int ir = 0; ir < resourceTypes.length; ir++ ) {
                    ResourceType rt = resourceTypes[ ir ];
                    if ( rtName.equalsIgnoreCase( rt.getName() ) ) {
                        rtype = rt;
                    }
                }
                if ( rtype == null ) {
                    System.err.println( usage );
                    return 1;
                }
            }
            else if ( arg.startsWith( "-verb" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.startsWith( "+verb" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.equals( "-help" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
            else if ( arg.startsWith( "-" ) || arg.startsWith( "+" ) ) {
                System.err.println( usage );
                return 1;
            }
            else if ( location == null ) {
                it.remove();
                location = arg;
            }
            else {
                System.err.println( usage );
                return 1;
            }
        }
        if ( location == null ) {
            System.err.println( usage );
            return 1;
        }

        // Set logging levels in accordance with flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Interpret location.
        final URL url;
        if ( location == null ) {
            System.err.println( usage );
            return 1;
        }
        else {
            url = getUrl( location );
        }
        if ( url == null ) {
            logger_.warning( "Bad location: " + location );
        }

        // Make sure we have a resource type.  This may have been supplied
        // on the command line, but if not, try to identify it by retrieving
        // the metadata or content of the resource itself.
        if ( url != null ) {
            if ( rtype == null ) {
                rtype = ResourceType.readHeadResourceType( url );
            }
            if ( rtype == null ) {
                rtype = ResourceType.readContentResourceType( url );
            }
            if ( rtype == null ) {
                logger_.warning( "Unknown resource type: " + url );
            }
        }

        // Prepare client metadata.
        Metadata meta = new Metadata();
        meta.setName( "SampLoad" );
        try {
            meta.setIconUrl( UtilServer.getInstance()
                            .exportResource( ICON_URL ).toString() );
        }
        catch ( Exception e ) {
            logger_.log( Level.WARNING, "Can't export application icon", e );
        }
        meta.setDescriptionText( new StringBuffer()
            .append( "Browser helper application to forward downloaded files" )
            .append( " (" + rtype + ") " )
            .append( "to SAMP Hub" )
            .toString() );
        meta.put( "author.name", "Mark Taylor" );

        // Prepare hub registration.
        ClientProfile profile = DefaultClientProfile.getProfile();
        GuiHubConnector connector = new GuiHubConnector( profile );
        connector.declareMetadata( meta );
        connector.declareSubscriptions( connector.computeSubscriptions() );
        connector.setActive( rtype != null );

        // Send message under GUI control.
        SampLoad dialog = new SampLoad( connector, rtype, url, location );
        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setVisible( true );

        connector.setActive( false );
        return 0;
    }

    /**
     * Main method.
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        System.exit( status );
    }
}
