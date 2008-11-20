package org.astrogrid.samp.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.AbstractListModel;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
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
    private final ListModel clientListModel_;

    /**
     * Constructor.
     *
     * @param   random  random number generator
     */
    public MessageTrackerHubService( Random random ) {
        super( random );
        callMap_ = Collections.synchronizedMap( new HashMap() );
        notifySet_ = Collections.synchronizedSet( new HashSet() );
        clientListModel_ =
            new MessageTrackerClientListModel( super.getClientListModel() );
    }

    public ListModel getClientListModel() {
        return clientListModel_;
    }

    public HubClient createClient( String privateKey, String publicId ) {
        return new MessageTrackerHubClient( privateKey, publicId );
    }

    public JFrame createHubWindow() {
        HubView hubView = new HubView();
        hubView.setClientListModel( getClientListModel() );
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
        final Transmission trans = (Transmission) callMap_.get( msgId );
        final Response resp = Response.asResponse( response );
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
     * Returns a list model of the transmissions sent by a given client.
     * This model is updated automatically as the hub forwards messages and
     * responses.
     *
     * @return  list model containing {@link Transmission} objects
     */
    public ListModel getTxListModel( Client client ) {
        return client instanceof MessageTrackerHubClient
             ? ((MessageTrackerHubClient) client).txListModel_
             : null;
    }

    /**
     * Returns a list model of the transmissions received by a given client.
     * This model is updated automatically as the hub forwards messages and
     * responses.
     *
     * @return  list model containing {@link Transmission} objects
     */
    private ListModel getRxListModel( Client client ) {
        return client instanceof MessageTrackerHubClient 
             ? ((MessageTrackerHubClient) client).rxListModel_
             : null;
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
            rxListModel_ = new TransmissionListModel();
            txListModel_ = new TransmissionListModel();
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
                getClientSet().getFromPublicId( senderId );
            final MessageTrackerHubClient recipient = client_;
            final Transmission trans = 
                new Transmission( sender, recipient, msg, msgId );
            callMap_.put( msgId, trans );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    sender.txListModel_.add( trans );
                    recipient.rxListModel_.add( trans );
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
                getClientSet().getFromPublicId( senderId );
            final MessageTrackerHubClient recipient = client_;
            final Transmission trans =
                new Transmission( sender, recipient, msg, null );
            notifySet_.add( trans );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    sender.txListModel_.add( trans );
                    recipient.rxListModel_.add( trans );
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
     * ListModel implementation for containing {@link Transmission} objects.
     */
    private static class TransmissionListModel extends AbstractListModel {
        private final List list_;
        private final ChangeListener changeListener_;

        /**
         * Constructor.
         */
        public TransmissionListModel() {
            list_ = new ArrayList();
            changeListener_ = new ChangeListener() {
                public void stateChanged( ChangeEvent evt ) {
                    Object src = evt.getSource();
                    assert src instanceof Transmission;
                    if ( src instanceof Transmission ) {
                        transmissionChanged( (Transmission) src );
                    }
                }
            };
        }

        /**
         * Called whenever a transmission which is in this list has changed
         * state.
         */
        private void transmissionChanged( Transmission trans ) {
            int index = list_.indexOf( trans );
            if ( index >= 0 ) {
                if ( trans.isDone() ) {
                    list_.remove( index );
                    fireIntervalRemoved( trans, index, index );
                }
                else {
                    fireContentsChanged( trans, index, index );
                }
            }
        }

        public int getSize() {
            return list_.size();
        }

        public Object getElementAt( int index ) {
            return list_.get( index );
        }

        /**
         * Adds a transmission to this list.
         *
         * @param  trans  transmission to add
         */
        public void add( Transmission trans ) {
            int index = list_.size();
            list_.add( trans );
            fireIntervalAdded( trans, index, index );
            trans.addChangeListener( changeListener_ );
        }

        /**
         * Removes a transmission from this list.
         *
         * @param  trans  transmission to remove
         */
        public void remove( Transmission trans ) {
            int index = list_.indexOf( trans );
            trans.removeChangeListener( changeListener_ );
            if ( index >= 0 ) {
                list_.remove( index );
                fireIntervalRemoved( trans, index, index );
            }
        }
    }

    /**
     * ClientListModel implementation used by this GuiHubService.
     */
    private class MessageTrackerClientListModel extends AbstractListModel {
        private final ListModel baseModel_;
        private Set transModelSet_;
        private final ListDataListener transListener_;

        /**
         * Constructor.
         *
         * @return   baseModel  base list model, containing Client objects
         */
        MessageTrackerClientListModel( ListModel baseModel ) {
            transModelSet_ = new HashSet();
            baseModel_ = baseModel;

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
                        int nclient = baseModel_.getSize();
                        for ( int ic = 0; ic < nclient; ic++ ) {
                            Client client =
                                (Client) baseModel_.getElementAt( ic );
                            if ( trans.getSender().equals( client ) ||
                                 trans.getReceiver().equals( client ) ) {
                                fireContentsChanged( trans, ic, ic );
                            }
                        }
                    }
                }
            };

            // Ensure that the transListener is listening to changes from 
            // the right places.
            baseModel_.addListDataListener( new ListDataListener() {
                public void contentsChanged( ListDataEvent evt ) {
                    fireContentsChanged( evt.getSource(),
                                         evt.getIndex0(), evt.getIndex1() );
                    adjustTransmissionListeners();
                }
                public void intervalAdded( ListDataEvent evt ) {
                    fireIntervalAdded( evt.getSource(),
                                       evt.getIndex0(), evt.getIndex1() );
                    adjustTransmissionListeners();
                }
                public void intervalRemoved( ListDataEvent evt ) {
                    fireIntervalRemoved( evt.getSource(),
                                         evt.getIndex0(), evt.getIndex1() );
                    adjustTransmissionListeners();
                }
            } );
        }

        public int getSize() {
            return baseModel_.getSize();
        }

        public Object getElementAt( int index ) {
            return baseModel_.getElementAt( index );
        }

        /**
         * Called when there is any change to the client list.
         * It makes sure that the listener tracking changes to Transmission
         * lists is listening to the right list models.
         */
        private void adjustTransmissionListeners() {
            int nClient = baseModel_.getSize();
            Set tSet = new HashSet();
            for ( int i = 0; i < nClient; i++ ) {
                Client client = (Client) baseModel_.getElementAt( i );
                tSet.add( getRxListModel( client ) );
                tSet.add( getTxListModel( client ) );
            }
            Set addedSet = new HashSet( tSet );
            addedSet.removeAll( transModelSet_ );
            for ( Iterator it = addedSet.iterator(); it.hasNext(); ) {
                ((TransmissionListModel)
                 it.next()).addListDataListener( transListener_ );
            }
            Set removedSet = new HashSet( transModelSet_ );
            removedSet.removeAll( tSet );
            for ( Iterator it = removedSet.iterator(); it.hasNext(); ) {
                ((TransmissionListModel)
                  it.next()).removeListDataListener( transListener_ );
            }
            transModelSet_ = tSet;
        }
    }

    /**
     * Cell renderer used by this GuiHubService.
     */
    private class MessageTrackerCellRenderer extends ClientListCellRenderer {
        protected String getLabel( Client client ) {
            StringBuffer sbuf = new StringBuffer()
                .append( SampUtils.toString( client ) );
            if ( client instanceof MessageTrackerHubClient ) {
                MessageTrackerHubClient mtClient =
                   (MessageTrackerHubClient) client;
                ListModel rxModel = mtClient.rxListModel_;
                if ( rxModel != null ) {
                    sbuf.append( " <" )
                        .append( rxModel.getSize() );
                }
                ListModel txModel = mtClient.txListModel_;
                if ( txModel != null ) {
                    sbuf.append( " >" )
                        .append( txModel.getSize() );
                }
            }
            return sbuf.toString();
        }
    }
}
