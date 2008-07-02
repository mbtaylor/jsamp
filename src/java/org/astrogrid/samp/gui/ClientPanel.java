package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
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

    public ClientPanel() {
        Box main = Box.createVerticalBox();

        Box identBox = Box.createVerticalBox();
        identBox.setBorder( createTitledBorder( "Identity" ) );
        Box idBox = Box.createHorizontalBox();
        idField_ = new JTextField();
        idField_.setEditable( false );
        idBox.add( new JLabel( "Public ID: " ) );
        idBox.add( idField_ );
        identBox.add( idBox );
        main.add( identBox );

        metaBox_ = Box.createVerticalBox();
        metaBox_.setBorder( createTitledBorder( "Metadata" ) );
        JScrollPane metaScroller = new JScrollPane( metaBox_ );
        metaScroller.setPreferredSize( new Dimension( WIDTH, 120 ) );
        main.add( metaScroller );

        Box subsBox = Box.createVerticalBox();
        subsBox.setBorder( createTitledBorder( "Subscriptions" ) );
        subsList_ = new JList();
        JScrollPane subsScroller = new JScrollPane( subsList_ );
        subsScroller.setPreferredSize( new Dimension( WIDTH, 120 ) );
        subsBox.add( subsScroller );
        main.add( subsBox );
    }

    public void setClient( Client client ) {
        idField_.setText( client.getId() );
        setMetadata( client.getMetadata() );
        setSubscriptions( client.getSubscriptions() );
        client_ = client;
    }

    public Client getClient() {
        return client_;
    }

    public void setMetadata( Metadata meta ) {
        metaBox_.removeAll();
        for ( Iterator it = meta.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            metaBox_.add( new JLabel( key ) );
            Box valueBox = Box.createHorizontalBox();
            valueBox.add( Box.createHorizontalStrut( 24 ) );
            valueBox.add( createViewer( value ) );
        }
    }

    public void setSubscriptions( Subscriptions subs ) {
        DefaultListModel listModel = new DefaultListModel();
        listModel.copyInto( subs.keySet().toArray() );
        subsList_.setModel( listModel );
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
        return BorderFactory
              .createTitledBorder( BorderFactory
                                  .createLineBorder( Color.BLACK ), title );
    }
}
