package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.AbstractListModel;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.border.Border;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.hub.HubClient;

/**
 * Component which displays details about a {@link org.astrogrid.samp.Client}.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class ClientPanel extends JPanel {

    private final JTextField idField_;
    private final JTextField profileField_;
    private final Box metaBox_;
    private final JList subsList_;
    private Client client_;
    private static final int INFO_WIDTH = 240;
    private final Logger logger_ =
        Logger.getLogger( ClientPanel.class.getName() );

    /**
     * Constructor.
     *
     * @param  hubLike  true if this will be displaying clients implementing
     *                  the HubClient interface
     */
    public ClientPanel( boolean hubLike ) {
        super( new BorderLayout() );
        JSplitPane splitter = new JSplitPane( JSplitPane.VERTICAL_SPLIT );
        splitter.setBorder( BorderFactory.createEmptyBorder() );
        splitter.setOneTouchExpandable( true );
        splitter.setResizeWeight( 0.5 );
        add( splitter, BorderLayout.CENTER );

        // Construct and place registration subpanel.
        Box regBox = Box.createVerticalBox();
        regBox.setBorder( createTitledBorder( "Registration" ) );
        Box idLine = Box.createHorizontalBox();
        idField_ = new JTextField();
        idField_.setEditable( false );
        idField_.setBorder( BorderFactory.createEmptyBorder() );
        idLine.add( new JLabel( "Public ID: " ) );
        idLine.add( idField_ );
        regBox.add( idLine );
        if ( hubLike ) {
            profileField_ = new JTextField();
            profileField_.setEditable( false );
            profileField_.setBorder( BorderFactory.createEmptyBorder() );
            Box profileLine = Box.createHorizontalBox();
            profileLine.add( new JLabel( "Profile: " ) );
            profileLine.add( profileField_ );
            regBox.add( profileLine );
        }
        else {
            profileField_ = null;
        }
        add( regBox, BorderLayout.NORTH );

        // Construct and place metadata subpanel.
        metaBox_ = Box.createVerticalBox();
        JPanel metaPanel = new JPanel( new BorderLayout() );
        metaPanel.add( metaBox_, BorderLayout.NORTH );
        JScrollPane metaScroller = new JScrollPane( metaPanel );
        metaScroller.setBorder( createTitledBorder( "Metadata" ) );
        metaScroller.setPreferredSize( new Dimension( INFO_WIDTH, 120 ) );
        splitter.setTopComponent( metaScroller );

        // Construct and place subscriptions subpanel.
        Box subsBox = Box.createVerticalBox();
        subsList_ = new JList();
        JScrollPane subsScroller = new JScrollPane( subsList_ );
        subsScroller.setBorder( createTitledBorder( "Subscriptions" ) );
        subsScroller.setPreferredSize( new Dimension( INFO_WIDTH, 120 ) );
        subsBox.add( subsScroller );
        splitter.setBottomComponent( subsBox );
    }

    /**
     * Updates this component to display the current state of a given client.
     *
     * @param  client  client, or null to clear display
     */
    public void setClient( Client client ) {
        idField_.setText( client == null ? null : client.getId() );
        if ( profileField_ != null ) {
            profileField_.setText( client instanceof HubClient
                                       ? ((HubClient) client).getProfileToken()
                                                             .getProfileName()
                                       : null );
        }
        setMetadata( client == null ? null : client.getMetadata() );
        setSubscriptions( client == null ? null : client.getSubscriptions() );
        client_ = client;
    }

    /**
     * Returns the most recently displayed client.
     *
     * @return  client
     */
    public Client getClient() {
        return client_;
    }

    /**
     * Updates this component's metadata panel to display the current state
     * of a given metadata object.
     *
     * @param  meta  metadata map, or null to clear metadata display
     */
    public void setMetadata( Metadata meta ) {
        metaBox_.removeAll();
        if ( meta != null ) {
            for ( Iterator it = meta.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                String key = (String) entry.getKey();
                Object value = entry.getValue();
                Box keyBox = Box.createHorizontalBox();
                keyBox.add( new JLabel( key + ":" ) );
                keyBox.add( Box.createHorizontalGlue() );
                metaBox_.add( keyBox );
                Box valueBox = Box.createHorizontalBox();
                valueBox.add( Box.createHorizontalStrut( 24 ) );
                valueBox.add( createViewer( value ) );
                metaBox_.add( valueBox );
            }
        }
        metaBox_.add( Box.createVerticalGlue() );
        metaBox_.repaint();
        metaBox_.revalidate();
    }

    /**
     * Updates this component's subscriptions panel to display the current
     * state of a given subscriptions object.
     *
     * @param  subs  subscriptions map, or null to clear subscriptions display
     */
    public void setSubscriptions( Subscriptions subs ) {
        final Object[] subscriptions = subs == null
                                     ? new Object[ 0 ]
                                     : subs.keySet().toArray();
        subsList_.setModel( new AbstractListModel() {
            public int getSize() {
                return subscriptions.length;
            }
            public Object getElementAt( int index ) {
                return subscriptions[ index ];
            }
        } );
    }

    /**
     * Attempts to open a URL in some kind of external browser.
     *
     * @param  url   URL to view
     */
    public void openURL( URL url ) throws IOException {
        BrowserLauncher.openURL( url.toString() );
    }

    /**
     * Returns a graphical component which displays a legal SAMP object
     * (SAMP map, list or string).
     *
     * @param   value  SAMP object
     * @return   new component displaying <code>value</code>
     */
    private JComponent createViewer( Object value ) {
        if ( value instanceof String ) {
            JTextField field = new JTextField();
            field.setEditable( false );
            field.setText( (String) value );
            field.setCaretPosition( 0 );
            try {
                final URL url = new URL( (String) value );
                field.setForeground( Color.BLUE );
                field.addMouseListener( new MouseAdapter() {
                    public void mouseClicked( MouseEvent evt ) {
                        try {
                            openURL( url );
                        }
                        catch ( IOException e ) {
                            Toolkit.getDefaultToolkit().beep();
                            logger_.warning( "Can't open URL " + url + e );
                        }
                    }
                } );
            }
            catch ( MalformedURLException e ) {
                // not a URL - fine
            }
            return field;
        }
        else if ( value instanceof List ) {
            return new JList( ((List) value).toArray() );
        }
        else if ( value instanceof Map ) {
            JEditorPane edPane =
                new JEditorPane( "text/html", toHtml( value ) );
            edPane.setEditable( false );
            edPane.setCaretPosition( 0 );
            return edPane;
        }
        else {
            return new JLabel( "???" );
        }
    }

    /**
     * Returns an HTML representation of a legal SAMP object
     * (SAMP map, list or string).
     *
     * @param  data  SAMP object
     * @return  HTML representation of <code>data</code>
     */
    private static String toHtml( Object data ) {
        StringBuffer sbuf = new StringBuffer();
        if ( data instanceof Map ) {
            sbuf.append( "<dl>\n" );
            for ( Iterator it = ((Map) data).entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                sbuf.append( "<dt>" )
                    .append( htmlEscape( String.valueOf( entry.getKey() ) ) )
                    .append( "</dt>\n" )
                    .append( "<dd>" )
                    .append( toHtml( entry.getValue() ) )
                    .append( "</dd>\n" );
            }
            sbuf.append( "</dl>\n" );
        }
        else if ( data instanceof List ) {
            sbuf.append( "<ul>\n" );
            for ( Iterator it = ((List) data).iterator(); it.hasNext(); ) {
                sbuf.append( "<li>" )
                    .append( toHtml( it.next() ) )
                    .append( "</li>\n" );
            }
            sbuf.append( "</ul>\n" );
        }
        else if ( data instanceof String ) {
            sbuf.append( htmlEscape( (String) data ) );
        }
        else {
            sbuf.append( "???" );
        }
        return sbuf.toString();
    }

    /**
     * Escapes a literal string for use within HTML text.
     *
     * @param   text  literal string
     * @return   escaped version of <code>text</code> safe for use within HTML
     */
    private static String htmlEscape( String text ) {
        int leng = text.length();
        StringBuffer sbuf = new StringBuffer( leng );
        for ( int i = 0; i < leng; i++ ) {
            char c = text.charAt( i );
            switch ( c ) {
                case '<':
                    sbuf.append( "&lt;" );
                    break;
                case '>':
                    sbuf.append( "&gt;" );
                    break;
                case '&':
                    sbuf.append( "&amp;" );
                    break;
                default:
                    sbuf.append( c );
            }
        }
        return sbuf.toString();
    }

    /**
     * Creates a titled border with a uniform style.
     *
     * @param  title  title text
     * @return  border
     */
    static Border createTitledBorder( String title ) {
        return BorderFactory.createCompoundBorder(
                   BorderFactory.createEmptyBorder( 5, 5, 5, 5 ),
                   BorderFactory.createTitledBorder(
                       BorderFactory.createLineBorder( Color.BLACK ), title ) );
    }
}
