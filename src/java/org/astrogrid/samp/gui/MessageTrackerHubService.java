package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.hub.ClientSet;
import org.astrogrid.samp.hub.HubClient;
import org.astrogrid.samp.hub.HubServiceException;
import org.astrogrid.samp.hub.Receiver;

/**
 * GuiHubService subclass which additionally keeps track of which messages
 * have been sent and received, and can provide a graphical display of these.
 * The overhead in maintaining the GUI display can be significant if there is
 * high volume of message traffic.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
public class MessageTrackerHubService extends GuiHubService {

    private final Map callMap_;
    private final Collection notifySet_;
    private MessageTrackerClientSet clientSet_;

    /**
     * Constructor.
     *
     * @param   random  random number generator
     */
    public MessageTrackerHubService( Random random ) {
        super( random );
        callMap_ = Collections.synchronizedMap( new HashMap() );
        notifySet_ = Collections.synchronizedSet( new HashSet() );
    }

    public void start() {
        super.start();
        clientSet_ = (MessageTrackerClientSet) getClientSet();
    }

    public ClientSet createClientSet() {
        return new MessageTrackerClientSet( getIdComparator() );
    }

    public HubClient createClient( String privateKey, String publicId ) {
        return new MessageTrackerHubClient( privateKey, publicId );
    }

    public JFrame createHubWindow() {
        HubView hubView = new HubView();
        hubView.setClientListModel( clientSet_ );
        hubView.getClientList()
               .setCellRenderer( new MessageTrackerCellRenderer() );
        JFrame frame = new JFrame( "SAMP Hub" );
        frame.getContentPane().add( hubView );
        frame.setIconImage( new ImageIcon( Client.class
                                          .getResource( "images/hub.png" ) )
                           .getImage() );
        frame.pack();
        return frame;
    }

    public void reply( Object callerKey, String msgId, Map response )
            throws HubServiceException {

        // Notify the transmission object corresponding to this response
        // that the response has been received.
        final Transmission trans =
            (Transmission) callMap_.get( getCallKey( getCaller( callerKey ),
                                                     msgId ) );
        final Response resp = Response.asResponse( response );
        assert trans != null;
        if ( trans != null ) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    trans.setResponse( resp );
                }
            } );
        }

        // Forward the call to the base implementation.
        super.reply( callerKey, msgId, response );
    }

    /**
     * Returns a key for use in the call map.
     * Identifies a call/response mode transmission.
     *
     * @param  receiver  message receiver
     * @param  msgId     message ID
     */
    private static Object getCallKey( Client receiver, String msgId ) {
        return new StringBuffer()
              .append( msgId )
              .append( "->" )
              .append( receiver.getId() )
              .toString();
    }

    /**
     * HubClient class used by this HubService implementation.
     */
    private class MessageTrackerHubClient extends HubClient {
        final TransmissionListModel rxListModel_;
        final TransmissionListModel txListModel_;

        /**
         * Constructor.
         *
         * @param  privateKey  private key
         * @param  publicId   public ID
         */
        public MessageTrackerHubClient( String privateKey, String publicId ) {
            super( privateKey, publicId );

            // Prepare list models for the transmissions sent/received by
            // a given client.  These models are updated as the hub forwards
            // messages and responses.  The contents of these models are
            // Transmission objects.
            txListModel_ = new TransmissionListModel();
            rxListModel_ = new TransmissionListModel();
        }

        public void setReceiver( Receiver receiver ) {
            super.setReceiver( receiver == null
                             ? null
                             : new MessageTrackerReceiver( receiver, this ) );
        }
    }

    /**
     * Wrapper implementation for the {@link Receiver} class which intercepts
     * calls to update sent and received transmission list models.
     */
    private class MessageTrackerReceiver implements Receiver {
        private final Receiver base_;
        private final MessageTrackerHubClient client_;

        /**
         * Constructor.
         *
         * @param  base   receiver on which this one is based
         * @param  client  hub client for which this receiver is operating
         */
        MessageTrackerReceiver( Receiver base,
                                MessageTrackerHubClient client ) {
            base_ = base;
            client_ = client;
        }

        public void receiveCall( String senderId, String msgId, Map message )
                throws HubServiceException {

            // When a call is received, create a corresponding Transmission
            // object and add it to both the send list of the sender and
            // the receive list of the recipient.
            Message msg = Message.asMessage( message );
            final MessageTrackerHubClient sender = 
                (MessageTrackerHubClient)
                clientSet_.getFromPublicId( senderId );
            final MessageTrackerHubClient recipient = client_;
            final Transmission trans = 
                new Transmission( sender, recipient, msg, msgId );
            Object callKey = getCallKey( recipient, msgId );
            assert ! callMap_.containsKey( callKey );
            callMap_.put( callKey, trans );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    sender.txListModel_.addTransmission( trans );
                    recipient.rxListModel_.addTransmission( trans );
                }
            } );

            // Forward the call to the base implementation.
            try {
                base_.receiveCall( senderId, msgId, message );
            }
            catch ( final HubServiceException e ) {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        trans.fail( e );
                    }
                } );
            }
        }

        public void receiveNotification( String senderId, Map message )
                throws HubServiceException {

            // When a notification is received, create a corresponding
            // Transmission object and add it to both the send list of the
            // sender and the receive list of the recipient.
            Message msg = Message.asMessage( message );
            final MessageTrackerHubClient sender =
                (MessageTrackerHubClient)
                clientSet_.getFromPublicId( senderId );
            final MessageTrackerHubClient recipient = client_;
            final Transmission trans =
                new Transmission( sender, recipient, msg, null );
            notifySet_.add( trans );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    sender.txListModel_.addTransmission( trans );
                    recipient.rxListModel_.addTransmission( trans );
                }
            } );

            // Forward the call to the base implementation.
            HubServiceException error;
            try {
                base_.receiveNotification( senderId, message );
                error = null;
            }
            catch ( HubServiceException e ) {
                error = e;
            }

            // Since it's a notify, no response will be forthcoming.
            // So signal a no-response (or send failure) directly.
            final Throwable err2 = error;
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    if ( err2 == null ) {
                        trans.setResponse( null );
                    }
                    else {
                        trans.fail( err2 );
                    }
                }
            } );
        }

        public void receiveResponse( String responderId, String msgTag,
                                     Map response )
                throws HubServiceException {

            // Just forward the call to the base implementation.
            // Handling the responses happens elsewhere (where we have the
            // msgId not the msgTag).
            base_.receiveResponse( responderId, msgTag, response );
        }
    }

    /**
     * ClientSet implementation used by this hub service.
     */
    private class MessageTrackerClientSet extends GuiClientSet {
        private final ListDataListener transListener_;

        /**
         * Constructor.
         *
         * @param  clientIdComparator  comparator for client IDs
         */
        MessageTrackerClientSet( Comparator clientIdComparator ) {
            super( clientIdComparator );

            // Prepare a listener which will be notified when a client's
            // send or receive list changes, or when any of the Transmission
            // objects in those lists changes state.
            transListener_ = new ListDataListener() {
                public void contentsChanged( ListDataEvent evt ) {
                    transmissionChanged( evt );
                }
                public void intervalAdded( ListDataEvent evt ) {
                    transmissionChanged( evt );
                }
                public void intervalRemoved( ListDataEvent evt ) {
                    transmissionChanged( evt );
                }
                private void transmissionChanged( ListDataEvent evt ) {
                    Object src = evt.getSource();
                    assert src instanceof Transmission;
                    if ( src instanceof Transmission ) {
                        Transmission trans = (Transmission) src;
                        int nclient = getSize();
                        for ( int ic = 0; ic < nclient; ic++ ) {
                            Client client = (Client) getElementAt( ic );
                            if ( trans.getSender().equals( client ) ||
                                 trans.getReceiver().equals( client ) ) {
                                ListDataEvent clientEvt =
                                    new ListDataEvent( trans,
                                                       ListDataEvent
                                                      .CONTENTS_CHANGED,
                                                       ic, ic );
                                fireListDataEvent( clientEvt );
                            }
                        }
                    }
                }
            };
        }

        public void add( HubClient client ) {
            final MessageTrackerHubClient mtClient =
                (MessageTrackerHubClient) client;
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    mtClient.txListModel_.addListDataListener( transListener_ );
                    mtClient.rxListModel_.addListDataListener( transListener_ );
                }
            } );
            super.add( client );
        }

        public void remove( HubClient client ) {
            super.remove( client );
            MessageTrackerHubClient mtClient =
                (MessageTrackerHubClient) client;
            final TransmissionListModel txListModel = mtClient.txListModel_;
            final TransmissionListModel rxListModel = mtClient.rxListModel_;
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    for ( int i = 0; i < txListModel.getSize(); i++ ) {
                        ((Transmission) txListModel.getElementAt( i ))
                                       .setSenderUnregistered();
                    }
                    for ( int i = 0; i < rxListModel.getSize(); i++ ) {
                        ((Transmission) rxListModel.getElementAt( i ))
                                       .setReceiverUnregistered();
                    }
                    txListModel.removeListDataListener( transListener_ );
                    rxListModel.removeListDataListener( transListener_ );
                }
            } );
        }
    }

    /**
     * Cell renderer used by this GuiHubService.
     * It draws a TransmissionListIcon to the right of the default 
     * representation.
     */
    private static class MessageTrackerCellRenderer
            extends ClientListCellRenderer {
        private MessageTrackerHubClient client_;
        private TransmissionListIcon transListIcon_;
        private final int padding_ = 10;

        /**
         * Constructor.
         */
        MessageTrackerCellRenderer() {
            ToolTipManager.sharedInstance().registerComponent( this );
        }

        protected void paintComponent( Graphics g ) {
            super.paintComponent( g );
            if ( transListIcon_ != null ) {
                Point p = getIconPosition();
                if ( g.hitClip( p.x, p.y, transListIcon_.getIconWidth(), 
                                          transListIcon_.getIconHeight() ) ) {
                    transListIcon_.paintIcon( this, g, p.x, p.y );
                }
            }
        }

        public Dimension getPreferredSize() {
            Dimension prefSize = super.getPreferredSize();
            if ( transListIcon_ != null ) {
                prefSize.width += transListIcon_.getIconWidth() + padding_;
            }
            return prefSize;
        }

        public String getToolTipText( MouseEvent evt ) {
            if ( transListIcon_ != null ) {
                Point iconPos = getIconPosition();
                Point p = new Point( evt.getPoint() );
                p.x -= iconPos.x;
                p.y -= iconPos.y;
                Transmission trans = transListIcon_.getTransmissionAt( p );
                if ( trans != null ) {
                    String mtype = trans.getMessage().getMType();
                    if ( client_ == trans.getSender() ) {
                        return mtype + " -> "
                             + SampUtils.toString( trans.getReceiver() );
                    }
                    else if ( client_ == trans.getReceiver() ) {
                        return mtype + " <- "
                             + SampUtils.toString( trans.getSender() );
                    }
                    else {
                        assert false;
                    }
                }
            }
            return null;
        }

        public Component getListCellRendererComponent( JList list, Object value,
                                                       int index, boolean isSel,
                                                       boolean hasFocus ) {
            Component c =
                super.getListCellRendererComponent( list, value, index,
                                                    isSel, hasFocus );
            if ( value instanceof MessageTrackerHubClient ) {
                client_ = (MessageTrackerHubClient) value;
                int size = c.getPreferredSize().height;
                if ( c instanceof JComponent ) {
                    Insets insets = ((JComponent) c).getInsets();
                    size -= insets.top + insets.bottom;
                }
                transListIcon_ =
                    new TransmissionListIcon( client_.rxListModel_,
                                              client_.txListModel_, size );
            }
            else {
                transListIcon_ = null;
            }
            return c;
        }

        /**
         * Returns the position at which the transmission list icon should
         * be drawn.
         *
         * @return   icon base position
         */
        private Point getIconPosition() {
            Insets insets = getInsets();
            return new Point( insets.left + super.getPreferredSize().width
                                          + padding_,
                              insets.top );
        }
    }
}
