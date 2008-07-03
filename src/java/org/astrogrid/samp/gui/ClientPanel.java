package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.AbstractListModel;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.border.Border;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;

public class ClientPanel extends JPanel {

    private final JTextField idField_;
    private final Box metaBox_;
    private final JList subsList_;
    private Client client_;
    private static final int INFO_WIDTH = 240;

    public ClientPanel() {
        super( new BorderLayout() );
        Box main = Box.createVerticalBox();
        add( main );

        Box identBox = Box.createVerticalBox();
        identBox.setBorder( createTitledBorder( "Identity" ) );
        Box idBox = Box.createHorizontalBox();
        idField_ = new JTextField();
        idField_.setEditable( false );
        idBox.add( new JLabel( "Public ID: " ) );
        idBox.add( idField_ );
        identBox.add( idBox );
        add( identBox, BorderLayout.NORTH );

        metaBox_ = Box.createVerticalBox();
        JPanel metaPanel = new JPanel( new BorderLayout() );
        metaPanel.add( metaBox_, BorderLayout.NORTH );
        JScrollPane metaScroller = new JScrollPane( metaPanel );
        metaScroller.setBorder( createTitledBorder( "Metadata" ) );
        metaScroller.setPreferredSize( new Dimension( INFO_WIDTH, 120 ) );
        main.add( metaScroller );

        Box subsBox = Box.createVerticalBox();
        subsList_ = new JList();
        JScrollPane subsScroller = new JScrollPane( subsList_ );
        subsScroller.setBorder( createTitledBorder( "Subscriptions" ) );
        subsScroller.setPreferredSize( new Dimension( INFO_WIDTH, 120 ) );
        subsBox.add( subsScroller );
        main.add( subsBox );
    }

    public void setClient( Client client ) {
        idField_.setText( client == null ? null : client.getId() );
        setMetadata( client == null ? null : client.getMetadata() );
        setSubscriptions( client == null ? null : client.getSubscriptions() );
        client_ = client;
    }

    public Client getClient() {
        return client_;
    }

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

    private static JComponent createViewer( Object value ) {
        if ( value instanceof String ) {
            JTextField field = new JTextField();
            field.setEditable( false );
            field.setText( (String) value );
            return field;
        }
        else if ( value instanceof List ) {
            return new JList( ((List) value).toArray() );
        }
        else if ( value instanceof Map ) {
            JEditorPane edPane =
                new JEditorPane( "text/html", toHtml( value ) );
            edPane.setEditable( false );
            return edPane;
        }
        else {
            return new JLabel( "???" );
        }
    }

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

    private static Border createTitledBorder( String title ) {
        return BorderFactory.createCompoundBorder(
                   BorderFactory.createEmptyBorder( 5, 5, 5, 5 ),
                   BorderFactory.createTitledBorder(
                       BorderFactory.createLineBorder( Color.BLACK ), title ) );
    }
}
