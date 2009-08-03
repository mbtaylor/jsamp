package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.client.TrackedClientSet;

/**
 * HubConnector implementation which provides facilities for keeping track
 * of incoming and outgoing messages as well as the other GUI features.
 *
 * @author   Mark Taylor
 * @since    26 Nov 2008
 */
public class MessageTrackerHubConnector extends GuiHubConnector
                                        implements ClientTransmissionHolder {

    private final TransmissionListModel txListModel_;
    private final TransmissionListModel rxListModel_;
    private final TransmissionTableModel txTableModel_;
    private final TransmissionTableModel rxTableModel_;
    private final Map clientMap_;
    private final Map callAllMap_;
    private final Map txModelMap_;
    private final Map rxModelMap_;
    private final ListDataListener transListListener_;
    private final int listRemoveDelay_;
    private static final Logger logger_ =
        Logger.getLogger( MessageTrackerHubConnector.class.getName() );

    /**
     * Constructs a hub connector with default message tracker GUI expiry times.
     *
     * @param   profile  profile implementation
     */
    public MessageTrackerHubConnector( ClientProfile profile ) {
        this( profile, 500, 20000, 100 );
    }

    /**
     * Constructs a hub connector with specified message tracker GUI 
     * expiry times.
     * The delay times are times in milliseconds after message resolution
     * before message representations expire and hence remove themselves
     * from gui components.
     *
     * @param   profile  profile implementation
     * @param   listRemoveDelay   expiry delay for summary icons in client
     *                            list display
     * @param   tableRemoveDelay  expiry delay for rows in message
     *                            table display
     * @param   tableMaxRows   maximum number of rows in message table
     *                         (beyond this limit resolved messages may be
     *                         removed early)
     */
    public MessageTrackerHubConnector( ClientProfile profile,
                                       int listRemoveDelay,
                                       int tableRemoveDelay,
                                       int tableMaxRows ) {
        super( profile );
        listRemoveDelay_ = listRemoveDelay;
        transListListener_ = new ClientTransmissionListListener();
        txListModel_ = new TransmissionListModel( listRemoveDelay_ );
        rxListModel_ = new TransmissionListModel( listRemoveDelay_ );
        txListModel_.addListDataListener( transListListener_ );
        rxListModel_.addListDataListener( transListListener_ );
        txTableModel_ =
            new TransmissionTableModel( false, true,
                                        tableRemoveDelay, tableMaxRows );
        rxTableModel_ =
            new TransmissionTableModel( true, false,
                                        tableRemoveDelay, tableMaxRows );
        clientMap_ = getClientMap();
        callAllMap_ = new HashMap();  // access only from EDT
        txModelMap_ = new WeakHashMap();
        rxModelMap_ = new WeakHashMap();
    }

    /**
     * Returns a ListModel representing the pending messages sent using
     * this connector.
     * Elements of the model are {@link Transmission} objects.
     *
     * @return   transmission list model
     */
    public ListModel getTxListModel() {
        return txListModel_;
    }

    /**
     * Returns a ListModel representing the pending messages received using
     * this connector.
     * Elements of the model are {@link Transmission} objects.
     *
     * @return  transmission list model
     */
    public ListModel getRxListModel() {
        return rxListModel_;
    }

    public ListModel getTxListModel( Client client ) {
        if ( ! txModelMap_.containsKey( client ) ) {
            TransmissionListModel listModel =
                new TransmissionListModel( listRemoveDelay_ );
            listModel.addListDataListener( transListListener_ );
            txModelMap_.put( client, listModel );
        }
        return (ListModel) txModelMap_.get( client );
    }

    public ListModel getRxListModel( Client client ) {
        if ( ! rxModelMap_.containsKey( client ) ) {
            TransmissionListModel listModel =
                new TransmissionListModel( listRemoveDelay_ );
            listModel.addListDataListener( transListListener_ );
            rxModelMap_.put( client, listModel );
        }
        return (ListModel) rxModelMap_.get( client );
    }

    /**
     * Returns a component which displays messages currently being 
     * sent/received by this connector.
     *
     * @return  iconSize  height of icons in box
     */
    public JComponent createMessageBox( int iconSize ) {
        JComponent box =
            createMessageBox( iconSize, rxListModel_, txListModel_ );
        registerUpdater( box, ENABLE_COMPONENT );
        return box;
    }

    /**
     * Returns a component which displays messages in receiver and/or sender
     * list models.
     *
     * @param  iconSize  height of icons
     * @param  rxListModel   list model containing received
     *                       {@link Transmission} objects
     * @param  txListModel   list model containing sent
     *                       {@link Transmission} objects
     */
    public static JComponent createMessageBox( int iconSize,
                                               ListModel rxListModel,
                                               ListModel txListModel ) {
        final Color dtColor = UIManager.getColor( "Label.disabledText" );
        JComponent box = new JPanel() {
            final Color enabledFg = getForeground();
            final Color enabledBg = Color.WHITE;
            final Color disabledFg = null;
            final Color disabledBg = getBackground();
            public void setEnabled( boolean enabled ) {
                super.setEnabled( enabled );
                setForeground( enabled ? enabledFg : disabledFg );
                setBackground( enabled ? enabledBg : disabledFg );
            }
        };
        box.setLayout( new BoxLayout( box, BoxLayout.X_AXIS ) );
        if ( rxListModel != null ) {
            IconBox rxBox = new IconBox( iconSize );
            rxBox.setOpaque( false );
            rxBox.setTrailing( true );
            rxBox.setModel( rxListModel );
            rxBox.setRenderer( new TransmissionCellRenderer() {
                public String getToolTipText( IconBox iconBox, Object value,
                                              int index ) {
                    if ( value instanceof Transmission ) {
                        Transmission trans = (Transmission) value;
                        return new StringBuffer()
                            .append( trans.getMessage().getMType() )
                            .append( " <- " )
                            .append( trans.getSender().toString() )
                            .toString();
                    }
                    else {
                        return super.getToolTipText( iconBox, value, index );
                    }
                }
            } );
            Dimension prefSize = rxBox.getPreferredSize();
            prefSize.width = iconSize * 3;
            rxBox.setPreferredSize( prefSize );
            box.add( rxBox );
        }
        IconBox cBox = new IconBox( iconSize );
        cBox.setOpaque( false );
        cBox.setBorder( BorderFactory.createEmptyBorder( 0, 2, 0, 2 ) );
        cBox.setModel( new AbstractListModel() {
            public int getSize() {
                return 1;
            }
            public Object getElementAt( int index ) {
                return "app";
            }
        } );
        cBox.setRenderer( new TransmissionCellRenderer() );
        Dimension cSize = cBox.getPreferredSize();
        cBox.setMaximumSize( cSize );
        cBox.setMinimumSize( cSize );
        box.add( cBox );
        if ( txListModel != null ) {
            IconBox txBox = new IconBox( iconSize );
            txBox.setOpaque( false );
            txBox.setModel( txListModel );
            txBox.setRenderer( new TransmissionCellRenderer() {
                public String getToolTipText( IconBox iconBox, Object value,
                                              int index ) {
                    if ( value instanceof Transmission ) {
                        Transmission trans = (Transmission) value;
                        return new StringBuffer()
                            .append( trans.getMessage().getMType() )
                            .append( " -> " )
                            .append( trans.getSender().toString() )
                            .toString();
                    }
                    else {
                        return super.getToolTipText( iconBox, value, index );
                    }
                }
            } );
            Dimension prefSize = txBox.getPreferredSize();
            prefSize.width = iconSize * 3;
            txBox.setPreferredSize( prefSize );
            box.add( txBox );
        }
        box.setBackground( Color.WHITE );
        box.setBorder( createBoxBorder() );
        return box;
    }

    public ListCellRenderer createClientListCellRenderer() {
        MessageTrackerListCellRenderer renderer =
            new MessageTrackerListCellRenderer( this );
        renderer.setTransmissionCellRenderer( new TransmissionCellRenderer() {
            public String getToolTipText( IconBox iconBox, Object value,
                                          int index ) {
                return value instanceof Transmission
                     ? ((Transmission) value).getMessage().getMType()
                     : super.getToolTipText( iconBox, value, index );
            }
        } );
        return renderer;
    }

    public JComponent createMonitorPanel() {
        JTabbedPane tabber = new JTabbedPane();

        // Add client view tab.
        HubView hubView = new HubView();
        hubView.setClientListModel( getClientListModel() );
        hubView.getClientList()
               .setCellRenderer( createClientListCellRenderer() );
        tabber.add( "Clients", hubView );

        // Add received message tab.
        tabber.add( "Received Messages",
                    new TransmissionView( rxTableModel_ ) );

        // Add sent message tab.
        tabber.add( "Sent Messages",
                    new TransmissionView( txTableModel_ ) );

        // Position and return.
        JComponent panel = new JPanel( new BorderLayout() );
        panel.add( tabber, BorderLayout.CENTER );
        return panel;
    }

    protected HubConnection createConnection() throws SampException {
        HubConnection connection = super.createConnection();
        return connection == null
             ? null
             : new MessageTrackerHubConnection( connection );
    }

    /**
     * Schedules a new transmission to add to the appropriate list models.
     * May be called from any thread.
     *
     * @param  trans  transmission
     * @param  tx    true for send, false for receive
     */
    private void scheduleAddTransmission( final Transmission trans,
                                          final boolean tx ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                ( tx ? txTableModel_
                     : rxTableModel_ ).addTransmission( trans );
                ((TransmissionListModel) getTxListModel( trans.getSender() ))
                                        .addTransmission( trans );
                ((TransmissionListModel) getRxListModel( trans.getReceiver() ))
                                        .addTransmission( trans );
            }
        } );
    }

    /**
     * Schedules a response to be registered for a previously added 
     * transmission.
     * May be called from any thread.
     *
     * @param  trans  transmission
     * @param  response   response to associated with trans
     */
    private void scheduleSetResponse( final Transmission trans,
                                      final Response response ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                trans.setResponse( response );
            }
        } );
    }

    /**
     * Schedules an error to be registered for a previously added
     * transmission.
     * May be called from any thread.
     *
     * @param  trans  transmission
     * @param  error  exception
     */
    private void scheduleSetFailure( final Transmission trans,
                                     final Throwable error ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                trans.setError( error );
            }
        } );
    }

    /**
     * HubConnection object which intercepts calls to keep track of 
     * outgoing and incoming messages.
     */
    private class MessageTrackerHubConnection extends WrapperHubConnection {
        private Client selfClient_;

        /**
         * Constructor.
         *
         * @param   base  connection on which this one is based
         */
        MessageTrackerHubConnection( HubConnection base ) {
            super( base );
        }

        /**
         * Returns a Client object for use in Transmission objects
         * which represents this connection's owner.
         * This has to be the same object as is used in the client set,
         * otherwise the various models don't get updated correctly.
         * For this reason, it has to be obtained lazily, after the client set
         * has been initialised.
         *
         * @return   self client object
         */
        Client getSelfClient() {
            if ( selfClient_ == null ) {
                selfClient_ =
                    (Client) getClientMap().get( getRegInfo().getSelfId() );
                assert selfClient_ != null;
                txModelMap_.put( selfClient_, txListModel_ );
                rxModelMap_.put( selfClient_, rxListModel_ );
            }
            return selfClient_;
        }

        public void notify( final String recipientId, final Map msg )
                throws SampException {

            // Construct a transmission corresponding to this notify and
            // add it to the send list.
            Client recipient = (Client) clientMap_.get( recipientId );
            Transmission trans = recipient == null
                               ? null
                               : new Transmission( getSelfClient(), recipient,
                                                   Message.asMessage( msg ),
                                                   null, null );
            if ( trans != null ) {
                scheduleAddTransmission( trans, true );
            }

            // Do the actual send.
            try {
                super.notify( recipientId, msg );

                // Notify won't generate a response, so signal that now.
                if ( trans != null ) {
                    scheduleSetResponse( trans, null );
                }
            }

            // If the send failed, signal it.
            catch ( SampException e ) {
                if ( trans != null ) {
                    scheduleSetFailure( trans, e );
                }
                throw e;
            }
        }

        public List notifyAll( Map msg ) throws SampException {

            // Do the send.
            List recipientIdList = super.notifyAll( msg );

            // Construct a list of transmissions corresponding to this notify
            // and add them to the send list.
            final List transList = new ArrayList();
            Message message = Message.asMessage( msg );
            Client sender = getSelfClient();
            for ( Iterator it = recipientIdList.iterator(); it.hasNext(); ) {
                Client recipient =
                    (Client) clientMap_.get( (String) it.next() );
                if ( recipient != null ) {
                    Transmission trans =
                        new Transmission( sender, recipient, message,
                                          null, null );
                    scheduleAddTransmission( trans, true );

                    // Notify won't generate a response, so signal that now.
                    scheduleSetResponse( trans, null );
                }
            }
            return recipientIdList;
        }

        public String call( String recipientId, String msgTag, Map msg )
                throws SampException {

            // Construct a transmission corresponding to this call
            // and add it to the send list.
            Client recipient = (Client) clientMap_.get( recipientId );
            Transmission trans = recipient == null
                               ? null
                               : new Transmission( getSelfClient(), recipient,
                                                   Message.asMessage( msg ),
                                                   msgTag, null );
            if ( trans != null ) {
                scheduleAddTransmission( trans, true );
            }

            // Do the actual call.
            try {
                return super.call( recipientId, msgTag, msg );
            }

            // If the send failed, signal that since no reply will be
            // forthcoming.
            catch ( final SampException e ) {
                scheduleSetFailure( trans, e );
                throw e;
            }
        }

        public Map callAll( final String msgTag, Map msg )
                throws SampException {

            // This is a bit more complicated than the other cases.
            // We can't construct the list of transmissions before the send,
            // since we don't know which are the recipient clients.
            // But if we wait until after the delegated callAll() method
            // we may miss some early responses to it.  So we have to 
            // put in place a mechanism for dealing with responses before
            // we know exactly what they are responses to.
            // Prepare and store a CallAllHandler for this.
            final CallAllHandler cah = new CallAllHandler( msgTag );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    callAllMap_.put( msgTag, cah );
                }
            } );

            // Do the actual call.
            Map callMap = super.callAll( msgTag, msg );

            // Prepare a post-facto list of the transmissions which were sent.
            List transList = new ArrayList();
            Message message = Message.asMessage( msg );
            for ( Iterator it = callMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                String recipientId = (String) entry.getKey();
                Client sender = getSelfClient();
                Client recipient = (Client) clientMap_.get( recipientId );
                if ( recipient != null ) {
                    String msgId = (String) entry.getValue();
                    Transmission trans =
                        new Transmission( sender, recipient, message,
                                          msgTag, msgId );
                    scheduleAddTransmission( trans, true );
                    transList.add( trans );
                }
            }
            final Transmission[] transmissions =
                (Transmission[]) transList.toArray( new Transmission[ 0 ] );

            // And inform the CallAllHandler what the transmissions were, so 
            // it knows how to process (possibly already received) responses.
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    cah.setTransmissions( transmissions );
                }
            } );
            return callMap;
        }

        public Response callAndWait( String recipientId, Map msg,
                                     int timeout ) throws SampException {

            // Construct a transmission obejct corresponding to this call
            // and add it to the send list.
            Client recipient = (Client) clientMap_.get( recipientId );
            Transmission trans =
                recipient == null
                          ? null
                          : new Transmission( getSelfClient(), recipient,
                                              Message.asMessage( msg ),
                                              "<synchronous>",
                                              "<synchronous>" );
            if ( trans != null ) {
                scheduleAddTransmission( trans, true );
            }

            // Do the actual call.
            try {
                Response response =
                    super.callAndWait( recipientId, msg, timeout );

                // Inform the transmission of the response.
                if ( trans != null ) {
                    scheduleSetResponse( trans, response );
                }
                return response;
            }

            // In case of error, inform the transmission of failure.
            catch ( SampException e ) {
                if ( trans != null ) {
                    scheduleSetFailure( trans, e );
                }
                throw e;
            }
        }

        public void reply( final String msgId, final Map response )
                throws SampException {

            // Do the actual reply.
            Throwable err;
            try {
                super.reply( msgId, response );
                err = null;
            }
            catch ( Throwable e ) {
                err = e;
            }
            final Throwable error = err;

            // Inform the existing transmission on the receive list 
            // that the reply has been made.
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    int nt = rxListModel_.getSize();
                    for ( int i = 0; i < nt; i++ ) {
                        Transmission trans =
                            (Transmission) rxListModel_.getElementAt( i );
                        if ( msgId.equals( trans.getMessageId() ) ) {
                            trans.setResponse( Response
                                              .asResponse( response ) );
                            if ( error != null ) {
                                trans.setError( error );
                            }
                            return;
                        }
                    }
                    logger_.warning( "Orphan reply " + msgId
                                   + " - replier programming error?" );
                }
            } );
        }

        public void setCallable( CallableClient callable )
                throws SampException {

            // Install a wrapper-like callable client which can intercept
            // the calls to keep track of send/received messages.
            CallableClient mtCallable =
                new MessageTrackerCallableClient( callable, this );
            super.setCallable( mtCallable );
        }
    }

    /**
     * CallableClient wrapper class which intercepts calls to keep track
     * of sent and received messages.
     */
    private class MessageTrackerCallableClient implements CallableClient {
        private final CallableClient base_;
        private final MessageTrackerHubConnection connection_;

        /**
         * Constructor.
         *
         * @param   base  base callable
         * @param   connection  hub connection
         */
        MessageTrackerCallableClient( CallableClient base,
                                      MessageTrackerHubConnection connection ) {
            base_ = base;
            connection_ = connection;
        }

        /**
         * Returns a Client object for use in Transmission objects
         * which represents this connection's owner.
         *
         * @return  self client object
         */
        private Client getSelfClient() {
            return connection_.getSelfClient();
        }

        public void receiveCall( String senderId, String msgId, Message msg )
                throws Exception {

            // Construct a transmission corresponding to the incoming call
            // and add it to the receive list.
            Client sender = (Client) clientMap_.get( senderId );
            Transmission trans =
                sender == null ? null
                               : new Transmission( sender, getSelfClient(), msg,
                                                   null, msgId );
            if ( trans != null ) {
                scheduleAddTransmission( trans, false );
            }

            // Actually handle the call.
            try {
                base_.receiveCall( senderId, msgId, msg );
            }

            // If the call handler fails, inform the transmission.
            catch ( Exception e ) {
                scheduleSetFailure( trans, e );
                throw e;
            }
        }

        public void receiveNotification( String senderId, Message msg )
                throws Exception {
            Client sender = (Client) clientMap_.get( senderId );

            // Actually handle the notification.
            base_.receiveNotification( senderId, msg );

            // Construct a transmission corresponding to the incoming
            // notification and add it to the receive list.
            // Give it a null response immediately, since being a notify
            // it won't get another one.
            if ( sender != null ) {
                final Transmission trans =
                    new Transmission( sender, getSelfClient(), msg,
                                      null, null );
                scheduleAddTransmission( trans, false );
                scheduleSetResponse( trans, null );
            }
        }

        public void receiveResponse( final String responderId,
                                     final String msgTag,
                                     final Response response )
                throws Exception {

            // Actually handle the response.
            base_.receiveResponse( responderId, msgTag, response );

            // Update state of the send list.
            // This isn't foolproof - if a sender has re-used the same msgTag
            // for a call and a callAll this handling might get confused -
            // but then so would the sender.
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {

                    // If the message was sent using callAll, handle using
                    // the registered CallAllHandler.
                    CallAllHandler cah =
                        (CallAllHandler) callAllMap_.get( msgTag );
                    if ( cah != null ) {
                        cah.addResponse( responderId, response );
                    }

                    // Otherwise find the relevant Transmission in the 
                    // send list and inform it of the response.
                    else {
                        int nt = txListModel_.getSize();
                        for ( int i = 0; i < nt; i++ ) {
                            Transmission trans =
                                (Transmission) txListModel_.getElementAt( i );
                            if ( responderId.equals( trans.getReceiver()
                                                          .getId() ) &&
                                 msgTag.equals( trans.getMessageTag() ) ) {
                                trans.setResponse( response );
                                return;
                            }
                        }
                        logger_.warning( "Orphan reply " + msgTag 
                                       + " - possible hub error?" );
                    }
                }
            } );
        }
    }

    /**
     * Class used to keep track of outgoing callAll() messages.
     * It needs to be able to match Responses with Transmissions,
     * but the complication is that a Response may arrive either before
     * or after its corresponding Transmission is known.
     */
    private class CallAllHandler {
        private final String msgTag_;
        private final Map responseMap_;
        private Collection transSet_;

        /**
         * Constructor.
         *
         * @param   msgTag  message tag labelling the callAll send
         */
        CallAllHandler( String msgTag ) {
            msgTag_ = msgTag;
            responseMap_ = new HashMap();
        }

        /**
         * Called once when the list of transmissions corresponding to the
         * callAll invocation is known.
         *
         * @param  transmissions   list of transmission objects, one for each
         *                         callAll recipient
         */
        public void setTransmissions( Transmission[] transmissions ) {

            // Store transmissions for later.
            if ( transSet_ != null ) {
                throw new IllegalStateException();
            }
            transSet_ = new HashSet( Arrays.asList( transmissions ) );

            // Process any responses already in.
            for ( Iterator it = responseMap_.entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                String responderId = (String) entry.getKey();
                Response response = (Response) entry.getValue();
                processResponse( responderId, response );
            }
            retireIfDone();
        }

        /**
         * Supplies a response to the callAll invocation handled by this object.
         *
         * @param  responderId  client ID of responder
         * @param  response  response
         */
        public void addResponse( String responderId, Response response ) {

            // If we know what transmissions have been sent, we can process
            // this response directly.
            if ( transSet_ != null ) {
                processResponse( responderId, response );
                retireIfDone();
            }

            // Otherwise store the response and defer processing until we do.
            else {
                responseMap_.put( responderId, response );
            }
        }

        /**
         * Does the work of passing a received response to the relevant 
         * member of the transmission list.  
         * May only be called following {@link #setTransmissions}.
         * 
         * @param  responderId  client ID of responder
         * @param  response  response
         */
        private void processResponse( String responderId, Response response ) {
            assert transSet_ != null;
            for ( Iterator it = transSet_.iterator(); it.hasNext(); ) {
                Transmission trans = (Transmission) it.next();
                if ( trans.getReceiver().getId().equals( responderId ) ) {
                    trans.setResponse( response );
                    it.remove();
                    return;
                }
            }
            logger_.warning( "Orphan reply " + msgTag_
                           + " - possible hub error?" );
        }

        /**
         * Checks whether this object has any further work to do
         * (any more responses are expected) and if not uninstalls itself,
         * at which point it becomes unreachable and can be garbage collected.
         * May only be called following {@link #setTransmissions}.
         */
        private void retireIfDone() {
            assert transSet_ != null;
            if ( transSet_.isEmpty() ) {
                assert callAllMap_.containsKey( msgTag_ );
                callAllMap_.remove( msgTag_ );
            }
        }
    }

    /**
     * ListDataListener implementation which responds to transmission list
     * events and passes them on to the client list, since any changes to
     * the transmission list may change the way that a client is renderered
     * in the JList.
     */
    private class ClientTransmissionListListener implements ListDataListener {

        public void contentsChanged( ListDataEvent evt ) {
            transmissionChanged( evt );
        }

        public void intervalAdded( ListDataEvent evt ) {
            transmissionChanged( evt );
        }

        public void intervalRemoved( ListDataEvent evt ) {
            transmissionChanged( evt );
        }

        /**
         * Called when there is any change to a known transmission.
         */
        private void transmissionChanged( ListDataEvent evt ) {
            Object src = evt.getSource();
            assert src instanceof Transmission;
            if ( src instanceof Transmission ) {
                Transmission trans = (Transmission) src;
                TrackedClientSet clientSet = getClientSet();
                clientSet.updateClient( trans.getReceiver(), false, false );
                clientSet.updateClient( trans.getSender(), false, false );
            }
        }
    }
}
