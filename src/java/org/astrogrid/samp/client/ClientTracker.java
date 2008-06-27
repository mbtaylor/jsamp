package org.astrogrid.samp.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

class ClientTracker extends AbstractMessageHandler {

    private final ClientListModel clientModel_;
    private final Map clientMap_;
    private final Logger logger_ =
        Logger.getLogger( ClientTracker.class.getName() );

    private static final String REGISTER_MTYPE;
    private static final String UNREGISTER_MTYPE;
    private static final String METADATA_MTYPE;
    private static final String SUBSCRIPTIONS_MTYPE;
    private static final String[] TRACKED_MTYPES = new String[] {
        REGISTER_MTYPE = "samp.hub.event.register",
        UNREGISTER_MTYPE = "samp.hub.event.unregister",
        METADATA_MTYPE = "samp.hub.event.metadata",
        SUBSCRIPTIONS_MTYPE = "samp.hub.event.subscriptions",
    };

    public ClientTracker() {
        super( TRACKED_MTYPES );
        clientModel_ = new ClientListModel();
        clientMap_ = clientModel_.getClientMap();
    }

    public ListModel getClientListModel() {
        return clientModel_;
    }

    public Map getClientMap() {
        return clientMap_;
    }

    public void clear() {
        try {
            initialise( null );
        }
        catch ( SampException e ) {
            assert false;
        }
    }

    public void initialise( HubConnection connection ) throws SampException {
        String[] clientIds = connection == null
                           ? new String[ 0 ]
                           : connection.getRegisteredClients();
        int nc = clientIds.length;
        TrackedClient[] clients = new TrackedClient[ nc ];
        for ( int ic = 0; ic < nc; ic++ ) {
            clients[ ic ] = new TrackedClient( clientIds[ ic ] );
        }
        clientModel_.setClients( clients );
        for ( int ic = 0; ic < nc; ic++ ) {
            TrackedClient client = clients[ ic ];
            String id = client.getId();
            client.setMetadata( connection.getMetadata( id ) );
            client.setSubscriptions( connection.getSubscriptions( id ) );
            clientModel_.updatedClient( client );
        }
    }

    public Map processCall( HubConnection connection, String senderId,
                            Message message ) {
        String mtype = message.getMType();
        if ( ! mtype.equals( connection.getRegInfo().getHubId() ) ) {
            logger_.warning( "Hub admin message " + mtype + " received from "
                           + "non-hub client.  Acting on it anyhow" );
        }
        String id = (String) message.getParams().get( "id" );
        if ( id == null ) {
            throw new IllegalArgumentException( "id parameter missing in "
                                              + mtype );
        }
        if ( REGISTER_MTYPE.equals( mtype ) ) {
            clientModel_.addClient( new TrackedClient( id ) );
        }
        else if ( UNREGISTER_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            clientModel_.removeClient( client );
        }
        else if ( METADATA_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            client.setMetadata( (Map) message.getParams().get( "metadata" ) );
            clientModel_.updatedClient( client );
        }
        else if ( SUBSCRIPTIONS_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            client.setSubscriptions( (Map) message.getParams()
                                                  .get( "subscriptions" ) );
            clientModel_.updatedClient( client );
        }
        else {
            throw new IllegalArgumentException( mtype );
        }
        return null;
    }

    private static class TrackedClient implements Client {

        private final String id_;
        private Metadata metadata_;
        private Subscriptions subscriptions_;

        public TrackedClient( String id ) {
            id_ = id;
        }

        public String getId() {
            return id_;
        }

        void setMetadata( Map metadata ) {
            metadata_ = Metadata.asMetadata( metadata );
        }

        public Metadata getMetadata() {
            return metadata_;
        }

        void setSubscriptions( Map subscriptions ) {
            subscriptions_ = Subscriptions.asSubscriptions( subscriptions );
        }

        public Subscriptions getSubscriptions() {
            return subscriptions_;
        }

        public boolean equals( Object o ) {
            if ( o instanceof Client ) {
                TrackedClient other = (TrackedClient) o;
                return other.id_.equals( this.id_ );
            }
            else {
                return false;
            }
        }

        public int hashCode() {
            return id_.hashCode();
        }
    }

    private static class ClientListModel implements ListModel {

        private final List clientList_;
        private final List listenerList_;
        private final Map clientMap_;
        private final Map clientMapView_;

        ClientListModel() {
            listenerList_ = new ArrayList();
            clientList_ = Collections.synchronizedList( new ArrayList() );
            clientMap_ = Collections.synchronizedMap( new HashMap() );
            clientMapView_ = Collections.unmodifiableMap( clientMap_ );
        }

        public int getSize() {
            return clientList_.size();
        }

        public Object getElementAt( int index ) {
            return clientList_.get( index );
        }

        public void addListDataListener( ListDataListener l ) {
            listenerList_.add( l );
        }

        public void removeListDataListener( ListDataListener l ) {
            listenerList_.remove( l );
        }

        public synchronized void addClient( Client client ) {
            int index = clientList_.size();
            clientList_.add( client );
            clientMap_.put( client.getId(), client );
            fireListDataEvent( ListDataEvent.INTERVAL_ADDED, index, index );
        }

        public synchronized void removeClient( Client client ) {
            int index = clientList_.indexOf( client );
            boolean removed = clientList_.remove( client );
            Client c = (Client) clientMap_.remove( client.getId() );
            assert removed;
            assert client.equals( c );
            fireListDataEvent( ListDataEvent.INTERVAL_REMOVED, index, index );
        }

        public synchronized void setClients( Client[] clients ) {
            clientList_.clear();
            clientList_.addAll( Arrays.asList( clients ) );
            for ( int ic = 0; ic < clients.length; ic++ ) {
                Client client = clients[ ic ];
                clientMap_.put( client.getId(), client );
            }
            fireListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                               0, clientList_.size() - 1 );
        }

        public void updatedClient( Client client ) {
            int index = clientList_.indexOf( client );
            if ( index >= 0 ) {
                fireListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                                   index, index );
            }
        }

        public Map getClientMap() {
            return clientMapView_;
        }

        private void fireListDataEvent( int type, int index0, int index1 ) {
            if ( ! listenerList_.isEmpty() ) {
                final ListDataEvent evt =
                    new ListDataEvent( this, type, index0, index1 );
                if ( SwingUtilities.isEventDispatchThread() ) {
                    doFireEvent( evt );
                }
                else {
                    SwingUtilities.invokeLater( new Runnable() {
                        public void run() {
                            doFireEvent( evt );
                        }
                    } );
                }
            }
        }

        private void doFireEvent( ListDataEvent evt ) {
            int type = evt.getType();
            for ( Iterator it = listenerList_.iterator(); it.hasNext(); ) {
                ListDataListener listener = (ListDataListener) it.next();
                if ( type == ListDataEvent.INTERVAL_ADDED ) {
                    listener.intervalAdded( evt );
                }
                else if ( type == ListDataEvent.INTERVAL_REMOVED ) {
                    listener.intervalRemoved( evt );
                }
                else if ( type == ListDataEvent.CONTENTS_CHANGED ) {
                    listener.contentsChanged( evt );
                }
                else {
                    assert false;
                }
            }
        }
    }
}
