package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
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
public class MessageTrackerHubService extends GuiHubService
                                      implements ClientTransmissionHolder {

    private final Map callMap_;
    private final TransmissionTableModel transTableModel_;
    private final int listRemoveDelay_; 
    private final int tableRemoveDelay_;
    private final int tableMaxRows_;
    private MessageTrackerClientSet clientSet_;

   
    /**
     * Constructs a hub service with default message tracker GUI expiry times.
     *
     * @param   random  random number generator
     */
    public MessageTrackerHubService( Random random ) {
        this( random, 0, 20000, 100 );
    }

    /**
     * Constructs a hub service with specified message tracker GUI expiry times.
     * The delay times are times in milliseconds after message resolution
     * before message representations expire and hence remove themselves
     * from gui components.
     *
     * @param   random  random number generator
     * @param   listRemoveDelay   expiry delay for summary icons in client
     *                            list display
     * @param   tableRemoveDelay  expiry delay for rows in message 
     *                            table display
     * @param   tableMaxRows   maximum number of rows in message table
     *                         (beyond this limit resolved messages may be
     *                         removed early)
     */
    public MessageTrackerHubService( Random random, int listRemoveDelay,
                                     int tableRemoveDelay, int tableMaxRows ) {
        super( random );
        listRemoveDelay_ = listRemoveDelay;
        tableRemoveDelay_ = tableRemoveDelay;
        tableMaxRows_ = tableMaxRows;
        callMap_ = new HashMap();  // access only from EDT;
        transTableModel_ =
            new TransmissionTableModel( true, true, tableRemoveDelay_,
                                        tableMaxRows_ );
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

    public JComponent createHubPanel() {
        JTabbedPane tabber = new JTabbedPane();

        // Add client view tab.
        HubView hubView = new HubView();
        hubView.setClientListModel( getClientListModel() );
        JList jlist = hubView.getClientList();
        jlist.setCellRenderer( new MessageTrackerListCellRenderer( this ) );
        jlist.addMouseListener( new HubClientPopupListener( this ) );
        tabber.add( "Clients", hubView );

        // Add messages tab.
        tabber.add( "Messages", new TransmissionView( transTableModel_ ) );

        // Position and return.
        JComponent panel = new JPanel( new BorderLayout() );
        panel.add( tabber, BorderLayout.CENTER );
        return panel;
    }

    /**
     * Returns a ListModel representing the pending messages sent 
     * from a given client.
     * Elements of the model are {@link Transmission} objects.
     *
     * @param   client  client owned by this hub service
     * @return  transmission list model
     */
    public ListModel getTxListModel( Client client ) {
        return client instanceof MessageTrackerHubClient
             ? ((MessageTrackerHubClient) client).txListModel_
             : null;
    }

    /**
     * Returns a ListModel representing the pending messages received
     * by a given client.
     * Elements of the model are {@link Transmission} objects.
     *
     * @param  client  client owned by this hub service
     * @return   transmission list model
     */
    public ListModel getRxListModel( Client client ) {
        return client instanceof MessageTrackerHubClient
             ? ((MessageTrackerHubClient) client).rxListModel_
             : null;
    }

    public void reply( Object callerKey, String msgId, final Map response )
            throws HubServiceException {

        // Notify the transmission object corresponding to this response
        // that the response has been received.
        final Object callKey = getCallKey( getCaller( callerKey ), msgId );
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Transmission trans = (Transmission) callMap_.remove( callKey );
                if ( trans != null ) {
                    trans.setResponse( Response.asResponse( response ) );
                }
            }
        } );

        // Forward the call to the base implementation.
        super.reply( callerKey, msgId, response );
    }

    /**
     * Registers a newly created transmission with internal data models
     * as required.
     *
     * @param   trans  new transmission to track
     */
    private void addTransmission( Transmission trans ) {
        ((MessageTrackerHubClient) trans.getSender())
                                  .txListModel_.addTransmission( trans );
        ((MessageTrackerHubClient) trans.getReceiver())
                                  .rxListModel_.addTransmission( trans );
        transTableModel_.addTransmission( trans );
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
            txListModel_ = new TransmissionListModel( listRemoveDelay_ );
            rxListModel_ = new TransmissionListModel( listRemoveDelay_ );
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
            MessageTrackerHubClient sender = 
                (MessageTrackerHubClient)
                clientSet_.getFromPublicId( senderId );
            MessageTrackerHubClient recipient = client_;
            final Transmission trans = 
                new Transmission( sender, recipient, msg, null, msgId );
            final Object callKey = getCallKey( recipient, msgId );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    assert ! callMap_.containsKey( callKey );
                    callMap_.put( callKey, trans );
                    addTransmission( trans );
                }
            } );

            // Forward the call to the base implementation.
            try {
                base_.receiveCall( senderId, msgId, message );
            }
            catch ( final HubServiceException e ) {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        trans.setError( e );
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
            MessageTrackerHubClient sender =
                (MessageTrackerHubClient)
                clientSet_.getFromPublicId( senderId );
            MessageTrackerHubClient recipient = client_;
            final Transmission trans =
                new Transmission( sender, recipient, msg, null, null );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    addTransmission( trans );
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
                        trans.setError( err2 );
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
            MessageTrackerHubClient mtClient = (MessageTrackerHubClient) client;
            final ListModel txListModel = mtClient.txListModel_;
            final ListModel rxListModel = mtClient.rxListModel_;
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    txListModel.addListDataListener( transListener_ );
                    rxListModel.addListDataListener( transListener_ );
                }
            } );
            super.add( client );
        }

        public void remove( HubClient client ) {
            super.remove( client );
            MessageTrackerHubClient mtClient = (MessageTrackerHubClient) client;
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
}
