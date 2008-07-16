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

/**
 * Message handler which watches hub event messages to keep track of
 * what clients are currently registered and what their attributes are
 * on behalf of the hub.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
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

    /**
     * Constructor.
     */
    public ClientTracker() {
        super( TRACKED_MTYPES );
        clientModel_ = new ClientListModel();
        clientMap_ = clientModel_.getClientMap();
    }

    /**
     * Returns a ListModel representing the state of registered clients.
     * Elements are {@link org.astrogrid.samp.Client} instances.
     *
     * @return  list model
     */
    public ListModel getClientListModel() {
        return clientModel_;
    }

    /**
     * Returns a map representing the state of registered clients.
     * The map keys are client public IDs and the values are 
     * {@link org.astrogrid.samp.Client} objects.
     * @return  id-&gt;Client map
     */
    public Map getClientMap() {
        return clientMap_;
    }

    /**
     * Removes all clients from the list.
     */
    public void clear() {
        try {
            initialise( null );
        }
        catch ( SampException e ) {
            assert false;
        }
    }

    /**
     * Initialises this tracker from a hub connection.
     * It is interrogated to find the current list of registered clients
     * and their attributes.
     *
     * @param  connection  hub connection; may be null for no connection
     */
    public void initialise( HubConnection connection ) throws SampException {
        String[] clientIds;

        // If connection is null, there are no registered clients.
        if ( connection == null ) {
            clientIds = new String[ 0 ];
        }

        // If connection is live, get the list of other registered clients,
        // and don't forget to add an entry for self, which 
        // getRegisteredClients() excludes.
        else {
            String[] otherIds = connection.getRegisteredClients();
            clientIds = new String[ otherIds.length + 1 ];
            System.arraycopy( otherIds, 0, clientIds, 0, otherIds.length );
            clientIds[ otherIds.length ] = connection.getRegInfo().getSelfId();
        }

        // Set up the client model to contain an entry for each registered
        // client.
        int nc = clientIds.length;
        TrackedClient[] clients = new TrackedClient[ nc ];
        for ( int ic = 0; ic < nc; ic++ ) {
            clients[ ic ] = new TrackedClient( clientIds[ ic ] );
        }
        clientModel_.setClients( clients );

        // Then get the metadata and subscriptions for each.
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
        if ( ! senderId.equals( connection.getRegInfo().getHubId() ) ) {
            logger_.warning( "Hub admin message " + mtype + " received from "
                           + "non-hub client.  Acting on it anyhow" );
        }
        String id = (String) message.getParams().get( "id" );
        if ( id == null ) {
            throw new IllegalArgumentException( "id parameter missing in "
                                              + mtype );
        }
        String selfId = connection.getRegInfo().getSelfId();
        if ( REGISTER_MTYPE.equals( mtype ) ) {
            clientModel_.addClient( new TrackedClient( id ) );
        }
        else if ( UNREGISTER_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            clientModel_.removeClient( client );
        }
        else if ( METADATA_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            if ( client != null ) {
                client.setMetadata( (Map) message.getParams()
                                                 .get( "metadata" ) );
                clientModel_.updatedClient( client );
            }
            else if ( id.equals( connection.getRegInfo().getSelfId() ) ) {
                // ignore - just haven't added self to client list yet
            }
            else {
                logger_.info( "No known client " + id );
            }
        }
        else if ( SUBSCRIPTIONS_MTYPE.equals( mtype ) ) {
            TrackedClient client = (TrackedClient) clientMap_.get( id );
            if ( client != null ) {
                client.setSubscriptions( (Map) message.getParams()
                                                      .get( "subscriptions" ) );
                clientModel_.updatedClient( client );
            }
            else if ( id.equals( connection.getRegInfo().getSelfId() ) ) {
                // ignore - just haven't added self to client list yet
            }
            else {
                logger_.info( "No known client " + id );
            }
        }
        else {
            throw new IllegalArgumentException( "Shouldn't have received MType"
                                              + mtype );
        }
        return null;
    }

    /**
     * Client implementation used to populate internal data structures.
     * It just implements the Client interface as well as adding mutators
     * for metadata and subscriptions, and providing an equals method based
     * on public id.
     */
    private static class TrackedClient implements Client {

        private final String id_;
        private Metadata metadata_;
        private Subscriptions subscriptions_;

        /**
         * Constructor.
         *
         * @param  id  client public id
         */
        public TrackedClient( String id ) {
            id_ = id;
        }

        public String getId() {
            return id_;
        }

        public Metadata getMetadata() {
            return metadata_;
        }

        public Subscriptions getSubscriptions() {
            return subscriptions_;
        }

        /**
         * Sets this client's metadata.
         *
         * @param  metadata  new metadata
         */
        void setMetadata( Map metadata ) {
            metadata_ = Metadata.asMetadata( metadata );
        }

        /**
         * Sets this client's subscriptions.
         *
         * @param  subscriptions  new subscriptions
         */
        void setSubscriptions( Map subscriptions ) {
            subscriptions_ = Subscriptions.asSubscriptions( subscriptions );
        }

        public boolean equals( Object o ) {
            if ( o instanceof TrackedClient ) {
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

        public String toString() {
            StringBuffer sbuf = new StringBuffer();
            sbuf.append( id_ );
            String name = metadata_ == null ? null
                                            : metadata_.getName();
            if ( name != null ) {
                sbuf.append( '(' )
                    .append( name )
                    .append( ')' );
            }
            return sbuf.toString();
        }
    }

    /**
     * ListModel implementation for holding Client objects.
     */
    private static class ClientListModel implements ListModel {

        private final List clientList_;
        private final List listenerList_;
        private final Map clientMap_;
        private final Map clientMapView_;

        /**
         * Constructor.
         */
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

        /**
         * Adds a client to this model.
         * Listeners are informed.  May be called from any thread.
         *
         * @param  client  client to add
         */
        public synchronized void addClient( Client client ) {
            int index = clientList_.size();
            clientList_.add( client );
            clientMap_.put( client.getId(), client );
            scheduleListDataEvent( ListDataEvent.INTERVAL_ADDED, index, index );
        }

        /**
         * Removes a client from this model.
         * Listeners are informed.  May be called from any thread.
         *
         * @param   client  client to remove
         */
        public synchronized void removeClient( Client client ) {
            int index = clientList_.indexOf( client );
            boolean removed = clientList_.remove( client );
            Client c = (Client) clientMap_.remove( client.getId() );
            assert removed;
            assert client.equals( c );
            scheduleListDataEvent( ListDataEvent.INTERVAL_REMOVED,
                                   index, index );
        }

        /**
         * Sets the contents of this model to a given list.
         * Listeners are informed.  May be called from any thread.
         *
         * @param  clients  current client list
         */
        public synchronized void setClients( Client[] clients ) {
            int nc = clientList_.size();
            clientList_.clear();
            if ( nc > 0 ) {
                scheduleListDataEvent( ListDataEvent.INTERVAL_REMOVED,
                                       0, nc - 1 );
            }
            clientList_.addAll( Arrays.asList( clients ) );
            for ( int ic = 0; ic < clients.length; ic++ ) {
                Client client = clients[ ic ];
                clientMap_.put( client.getId(), client );
            }
            if ( clients.length > 0 ) {
                scheduleListDataEvent( ListDataEvent.INTERVAL_ADDED,
                                       0, clients.length - 1 );
            }
        }

        /**
         * Notifies listeners that a given client's attributes (may) have
         * changed.  May be called from any thread.
         *
         * @param  client  modified client
         */
        public void updatedClient( Client client ) {
            int index = clientList_.indexOf( client );
            if ( index >= 0 ) {
                scheduleListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                                       index, index );
            }
        }

        /**
         * Returns a Map representing the client list.
         *
         * @return   id -&gt; Client map
         */
        public Map getClientMap() {
            return clientMapView_;
        }

        /**
         * Schedules notification of list data listeners about an event.
         * May be called from any thread.
         *
         * @param  type  ListDataEvent event type
         * @param  int  index0  ListDataEvent start index
         * @param  int  index1  ListDataEvent end index
         */
        private void scheduleListDataEvent( int type, int index0, int index1 ) {
            if ( ! listenerList_.isEmpty() ) {
                final ListDataEvent evt =
                    new ListDataEvent( this, type, index0, index1 );
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        fireEvent( evt );
                    }
                } );
            }
        }

        /**
         * Passes a ListDataEvent to all listeners.
         * Must be called from AWT event dispatch thread.
         *
         * @param  evt  event to forward
         */
        private void fireEvent( ListDataEvent evt ) {
            assert SwingUtilities.isEventDispatchThread();
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
