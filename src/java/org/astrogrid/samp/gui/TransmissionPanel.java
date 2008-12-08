package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.StringWriter;
import java.io.PrintWriter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampMap;
import org.astrogrid.samp.SampUtils;

/**
 * Component which displays the details of a given Transmission object.
 *
 * @author   Mark Taylor
 * @since    5 Dec 2008
 */
public class TransmissionPanel extends JPanel {

    private final JTextField mtypeField_;
    private final JTextField idField_;
    private final JTextField senderField_;
    private final JTextField receiverField_;
    private final JTextField statusField_;
    private final JTextArea messageField_;
    private final JTextArea responseField_;
    private final ChangeListener changeListener_;
    private Transmission trans_;

    /**
     * Constructor.
     */
    public TransmissionPanel() {
        super( new BorderLayout() );

        // Panel displaying one-line metadata items.
        Stack metaPanel = new Stack();
        metaPanel.setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ) );
        mtypeField_ = metaPanel.addField( "MType" );
        idField_ = metaPanel.addField( "Message ID" );
        senderField_ = metaPanel.addField( "Sender" );
        receiverField_ = metaPanel.addField( "Receiver" );
        statusField_ = metaPanel.addField( "Status" );

        // Panel displaying Message content.
        messageField_ = new JTextArea();
        messageField_.setEditable( false );
        messageField_.setLineWrap( false );
        JComponent messagePanel = new JPanel( new BorderLayout() );
        JComponent messageHeadBox = Box.createHorizontalBox();
        messageHeadBox.add( new JLabel( "Message" ) );
        messageHeadBox.add( Box.createHorizontalGlue() );
        messagePanel.add( messageHeadBox, BorderLayout.NORTH );
        messagePanel.add( new JScrollPane( messageField_ ),
                          BorderLayout.CENTER );

        // Panel displaying Response content.
        responseField_ = new JTextArea();
        responseField_.setEditable( false );
        responseField_.setLineWrap( false );
        JComponent responsePanel = new JPanel( new BorderLayout() );
        JComponent responseHeadBox = Box.createHorizontalBox();
        responseHeadBox.add( new JLabel( "Response" ) );
        responseHeadBox.add( Box.createHorizontalGlue() );
        responsePanel.add( responseHeadBox, BorderLayout.NORTH );
        responsePanel.add( new JScrollPane( responseField_ ),
                           BorderLayout.CENTER );

        // Place panels.
        JSplitPane splitter = new JSplitPane( JSplitPane.VERTICAL_SPLIT );
        splitter.setTopComponent( messagePanel );
        splitter.setBottomComponent( responsePanel );
        splitter.setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ) );
        splitter.setResizeWeight( 0.5 );
        add( metaPanel, BorderLayout.NORTH );
        add( splitter, BorderLayout.CENTER );

        // Prepare a listener to react to changes in the displayed
        // transmission object.
        changeListener_ = new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                updateState();
            }
        };
    }

    /**
     * Sets the transmission object being displayed.
     *
     * @param   trans   transmission object to display
     */
    public void setTransmission( Transmission trans ) {
        if ( trans_ != null ) {
            trans_.removeChangeListener( changeListener_ );
        }
        trans_ = trans;
        if ( trans_ != null ) {
            trans_.addChangeListener( changeListener_ );
        }
        updateState();
    }

    /**
     * Returns the transmission object currently being displayed.
     *
     * @return  transmission
     */
    public Transmission getTransmission() {
        return trans_;
    }

    /**
     * Invoked whenever the displayed transmission, or its characteristics,
     * change.
     */
    private void updateState() {
        if ( trans_ == null ) {
            mtypeField_.setText( null );
            idField_.setText( null );
            senderField_.setText( null );
            receiverField_.setText( null );
            statusField_.setText( null );
            messageField_.setText( null );
            responseField_.setText( null );
        }
        else {
            Message msg = trans_.getMessage();
            Response response = trans_.getResponse();
            Throwable error = trans_.getError();
            mtypeField_.setText( msg.getMType() );
            mtypeField_.setCaretPosition( 0 );
            idField_.setText( formatId( trans_ ) );
            idField_.setCaretPosition( 0 );
            senderField_.setText( formatClient( trans_.getSender() ) );
            senderField_.setCaretPosition( 0 );
            receiverField_.setText( formatClient( trans_.getReceiver() ) );
            receiverField_.setCaretPosition( 0 );
            statusField_.setText( trans_.getStatusString() );
            statusField_.setCaretPosition( 0 );
            messageField_.setText( SampUtils.formatObject( msg, 2 ) );
            messageField_.setCaretPosition( 0 );
            final String responseText;
            if ( response != null ) {
                responseText = SampUtils.formatObject( response, 2 );
            }
            else if ( error != null ) {
                StringWriter traceWriter = new StringWriter();
                error.printStackTrace( new PrintWriter( traceWriter ) );
                responseText = traceWriter.toString();
            }
            else {
                responseText = null;
            }
            responseField_.setText( responseText );
            responseField_.setCaretPosition( 0 );
        }
    }

    /**
     * Formats the identifier of a transmission as a string.
     *
     * @param   trans  transmission
     * @return  id string
     */
    private static String formatId( Transmission trans ) {
        String msgTag = trans.getMessageTag();
        String msgId = trans.getMessageId();
        StringBuffer idBuf = new StringBuffer();
        if ( msgTag != null ) {
            idBuf.append( "Tag: " )
                 .append( msgTag );
        }
        if ( msgId != null ) {
            if ( idBuf.length() > 0 ) {
                idBuf.append( "; " );
            }
            idBuf.append( "ID: " )
                 .append( msgId );
        }
        return idBuf.toString();
    }

    /**
     * Formats a client as a string.
     *
     * @param   client  client
     * @return   string
     */
    private static String formatClient( Client client ) {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( client.getId() );
        Metadata meta = client.getMetadata();
        if ( meta != null ) {
            String name = meta.getName();
            if ( name != null ) {
                sbuf.append( ' ' )
                    .append( '(' )
                    .append( name )
                    .append( ')' );
            }
        }
        return sbuf.toString();
    }

    /**
     * Component for aligning headings and text fields for metadata display.
     */
    private class Stack extends JPanel {
        private final GridBagLayout layer_;
        private int line_;

        /**
         * Constructor.
         */
        Stack() {
            layer_ = new GridBagLayout();
            setLayout( layer_ );
        }

        /**
         * Adds an item.
         *
         * @param   heading   text heading for item
         * @return   text field associated with heading
         */
        JTextField addField( String heading ) {
            GridBagConstraints cons = new GridBagConstraints();
            cons.gridy = line_++;
            cons.weighty = 1;
            cons.insets = new Insets( 2, 2, 2, 2 );
            cons.gridx = 0;
            cons.fill = GridBagConstraints.HORIZONTAL;
            cons.anchor = GridBagConstraints.WEST;
            cons.weightx = 0;
            JLabel label = new JLabel( heading + ":" );
            JTextField field = new JTextField();
            field.setEditable( false );
            layer_.setConstraints( label, cons );
            add( label );
            cons.gridx++;
            cons.weightx = 1;
            layer_.setConstraints( field, cons );
            add( field );
            return field;
        }
    }
}
