package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;

/**
 * ResultHandler which pops up a window displaying progress and success
 * of a message sent to one or many recipients.
 *
 * @author   Mark Taylor
 * @since    12 Nov 2008
 */
public class PopupResultHandler extends JFrame implements ResultHandler {

    private final Map clientMap_;
    private final int closeDelayMillis_;
    private final JCheckBox autoCloser_;

    /**
     * Constructor.
     *
     * @param   parent  parent component, used for window placement;
     *                  may be null
     * @param   title  window title
     * @param   msg   message which was sent
     * @param   recipients   clients from which responses are expected
     * @param   closeDelay   number of seconds after final response received
     *          that window closes automatically; if &lt;0, no auto close
     */
    public PopupResultHandler( Component parent, String title,
                               final Message msg, Client[] recipients,
                               int closeDelay ) {
        super( title );
        closeDelayMillis_ = closeDelay * 1000;

        // Set up manual window close action.
        Action closeAction = new AbstractAction( "Close" ) {
            public void actionPerformed( ActionEvent evt ) {
                PopupResultHandler.this.dispose();
            }
        };

        // Set up check box for configuring auto close
        autoCloser_ = closeDelayMillis_ >= 0
                    ? new JCheckBox( "Auto Close", true )
                    : null;

        // Set up per-client response handler objects
        clientMap_ = new HashMap();
        for ( int i = 0; i < recipients.length; i++ ) {
            Client client = recipients[ i ];
            clientMap_.put( client, new ClientHandler( client ) );
        }

        // Prepare component layout.
        Container content = getContentPane();
        content.setLayout( new BorderLayout() );
        JComponent main = new JPanel( new BorderLayout() );
        content.add( main );

        // Panel containing control buttons (window close)
        Box buttonBox = Box.createHorizontalBox();
        buttonBox.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
        if ( closeDelayMillis_ >= 0 ) {
            buttonBox.add( Box.createHorizontalGlue() );
            buttonBox.add( autoCloser_ );
        }
        buttonBox.add( Box.createHorizontalGlue() );
        buttonBox.add( new JButton( closeAction ) );
        buttonBox.add( Box.createHorizontalGlue() );
        main.add( buttonBox, BorderLayout.SOUTH );

        // Panel containing information about sent message.
        Box msgBox = Box.createVerticalBox();
        msgBox.setBorder( ClientPanel.createTitledBorder( "Message" ) );
        Box mtypeLine = Box.createHorizontalBox();
        mtypeLine.add( new JLabel( msg.getMType() ) );
        mtypeLine.add( Box.createHorizontalStrut( 10 ) );
        mtypeLine.add( Box.createHorizontalGlue() );
        Action msgDetailAction =
                new DetailAction( "Detail", "Show details of message sent",
                                  "Message Sent", "SAMP message sent:" ) {
            protected Map getSampMap() {
                return msg;
            }
        };
        mtypeLine.add( new JButton( msgDetailAction ) );
        msgBox.add( mtypeLine );
        main.add( msgBox, BorderLayout.NORTH );

        // Panel containing per-client response information.
        GridBagLayout layer = new GridBagLayout();
        GridBagConstraints cons = new GridBagConstraints();
        cons.weighty = 1;
        cons.gridx = 0;
        cons.gridy = 0;
        cons.insets = new Insets( 0, 2, 0, 2 );
        JComponent resultBox = new JPanel( layer );
        resultBox.setBorder( ClientPanel.createTitledBorder( "Responses" ) );
        for ( Iterator it = clientMap_.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            ClientHandler ch = (ClientHandler) entry.getValue();

            // Client name.
            cons.anchor = GridBagConstraints.WEST;
            cons.weightx = 0;
            cons.fill = GridBagConstraints.NONE;
            Component nameLabel = ch.nameLabel_;
            layer.setConstraints( nameLabel, cons );
            resultBox.add( nameLabel );

            // Response status.
            cons.gridx++;
            cons.anchor = GridBagConstraints.CENTER;
            cons.weightx = 1;
            cons.fill = GridBagConstraints.NONE;
            Component statusLabel = ch.statusLabel_;
            layer.setConstraints( statusLabel, cons );
            resultBox.add( statusLabel );

            // Detail button.
            cons.gridx++;
            cons.anchor = GridBagConstraints.WEST;
            cons.weightx = 0;
            cons.fill = GridBagConstraints.NONE;
            Component detailButton = new JButton( ch.responseDetailAction_ );
            layer.setConstraints( detailButton, cons );
            resultBox.add( detailButton );
            
            // Prepare for next row.
            cons.gridy++;
            cons.gridx = 0;
        }
        main.add( resultBox, BorderLayout.CENTER );

        // Display popup window.
        pack();
        if ( parent != null ) {
            setLocationRelativeTo( parent );
        }
        setVisible( true );
    }

    public void result( Client client, Response response ) {
        ClientHandler ch = (ClientHandler) clientMap_.get( client );
        if ( ch == null ) {
            throw new IllegalArgumentException( "Shouldn't happen" );
        }
        else {
            ch.result( response );
        }
    }

    public void done() {

        // Any clients which have not received responses yet, will not do so.
        // Make this clear in the status labels.
        for ( Iterator it = clientMap_.entrySet().iterator(); it.hasNext(); ) {
            ClientHandler ch =
                (ClientHandler) ((Map.Entry) it.next()).getValue();
            if ( ch.response_ == null ) {
                ch.result( null );
            }
        }

        // Arrange for auto close of window if required.
        if ( isVisible() && closeDelayMillis_ >= 0 &&
             autoCloser_.isSelected() ) {
            if ( closeDelayMillis_ == 0 ) {
                dispose();
            }
            else {
                new Timer( closeDelayMillis_, new ActionListener() {
                    public void actionPerformed( ActionEvent evt ) {
                        if ( autoCloser_.isSelected() ) {
                            dispose();
                        }
                    }
                } ).start();
            }
        }
    }

    /**
     * Handles per-client state for message wait/response information.
     */
    private class ClientHandler {
        final JLabel nameLabel_;
        final JLabel statusLabel_;
        final Action responseDetailAction_;
        Response response_;

        /**
         * Constructor.
         *
         * @param   client  client
         */
        ClientHandler( Client client ) {
            final String cName = SampUtils.toString( client );
            nameLabel_ = new JLabel( cName );
            statusLabel_ = new JLabel( "... Waiting ..." );
            statusLabel_.setForeground( new Color( 0x40, 0x40, 0x40 ) );
            responseDetailAction_ =
                    new DetailAction( "Detail", "Show details of response",
                                      cName + " Response",
                                      "SAMP Response from client " + cName
                                     + ":" ) {
                protected Map getSampMap() {
                    return response_;
                }
            };
            responseDetailAction_.setEnabled( response_ != null );
        }

        /**
         * Updates status according to a received response.
         * If the response is null, it indicates that no response is expected
         * in the future.
         *
         * @param   response  received response, or null
         */
        public void result( Response response ) {
            response_ = response;
            String status;
            String tip;
            boolean success;
            if ( response_ == null ) {
                success = false;
                status = "Aborted";
                tip = "Interruption in messaging system prevented "
                    + "receipt of response?";
            }
            else if ( response.isOK() ) {
                success = true;
                status = "OK";
                tip = "Message processed successfully";
            }
            else {
                success = false;
                status = "Fail";
                ErrInfo errInfo = response.getErrInfo();
                String errtxt = errInfo == null ? null : errInfo.getErrortxt();
                tip = errtxt;
                if ( errtxt != null ) {
                    status += " (" + errtxt + ")";
                }
            }
            statusLabel_.setText( status );
            statusLabel_.setForeground( success ? new Color( 0, 0x80, 0 )
                                                : new Color( 0x80, 0, 0 ) );
            statusLabel_.setToolTipText( tip );
            responseDetailAction_.setEnabled( response_ != null );
        }
    }

    /**
     * Action which will display a SampMap in a popup window.
     */
    private abstract class DetailAction extends AbstractAction {

        private final String popupTitle_;
        private final String heading_;

        /**
         * Constructor.
         *
         * @param  name  action name
         * @param  shortDesc   action short description (tool tip)
         * @param  popupTitle  title of popup window
         */
        DetailAction( String name, String shortDesc, String popupTitle,
                      String heading ) {
            super( name );
            putValue( SHORT_DESCRIPTION, shortDesc );
            popupTitle_ = popupTitle;
            heading_ = heading;
        }

        /**
         * Returns the map object which is to be displayed.
         * Invoked by {@link #actionPerformed}.
         *
         * @return   object to display
         */
        protected abstract Map getSampMap();

        public void actionPerformed( ActionEvent evt ) {

            // Make sure that autoclose is off, since otherwise these dialogues
            // will get disappeared at the same time as the owner window,
            // which is probably not what the user would expect.
            autoCloser_.setSelected( false );

            // Set up a text component containing the full map serialization.
            JTextArea ta = new JTextArea();
            ta.setLineWrap( false );
            ta.setEditable( false );
            ta.append( SampUtils.formatObject( getSampMap(), 3 ) );
            ta.setCaretPosition( 0 );

            // Wrap it in a scroll pane and size appropriately.
            Dimension size = ta.getPreferredSize();
            size.height = Math.min( size.height + 20, 200 );
            size.width = Math.min( size.width + 20, 360 );
            JScrollPane scroller = new JScrollPane( ta );
            scroller.setPreferredSize( size );

            // Put into a non-modal dialogue with decorations.
            final JDialog dialog =
                new JDialog( PopupResultHandler.this, popupTitle_, false );
            dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
            JComponent main = new JPanel( new BorderLayout() );
            main.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
            dialog.getContentPane().add( main );
            Action closeAction = new AbstractAction( "Close" ) {
                public void actionPerformed( ActionEvent evt ) {
                    dialog.dispose();
                }
            };
            Box buttonBox = Box.createHorizontalBox();
            buttonBox.add( Box.createHorizontalGlue() );
            buttonBox.add( new JButton( closeAction ) );
            buttonBox.add( Box.createHorizontalGlue() );
            Box headBox = Box.createHorizontalBox();
            headBox.add( new JLabel( heading_ ) );
            headBox.add( Box.createHorizontalGlue() );
            headBox.setBorder( BorderFactory.createEmptyBorder( 0, 0, 5, 0 ) );
            buttonBox.setBorder( BorderFactory
                                .createEmptyBorder( 5, 0, 0, 0 ) );
            main.add( headBox, BorderLayout.NORTH );
            main.add( buttonBox, BorderLayout.SOUTH );
            main.add( scroller, BorderLayout.CENTER ); 

            // Post the dialogue.
            dialog.setLocationRelativeTo( PopupResultHandler.this );
            dialog.pack();
            dialog.show();
        }
    }
}
