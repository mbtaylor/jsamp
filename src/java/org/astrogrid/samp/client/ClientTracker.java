package org.astrogrid.samp.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;

/**
 * Message handler which watches hub event messages to keep track of
 * what clients are currently registered and what their attributes are
 * on behalf of the hub.
 * The results are stored in an externally supplied {@link TrackedClientSet}
 * object.  This class tries its best to handle complications arising 
 * from the fact that calls concerning a client may arrive out of order 
 * (for instance declareMetadata before registration or after unregistration).
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class ClientTracker extends AbstractMessageHandler {

    private final TrackedClientSet clientSet_;
    private final Map clientMap_;
    private final OperationQueue opQueue_;
    private final static Logger logger_ =
        Logger.getLogger( ClientTracker.class.getName() );

    private static final int QUEUE_TIME = 10000;
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
     *
     * @param   clientSet  object used to record registered clients and their 
     *                     attributes
     */
    public ClientTracker( TrackedClientSet clientSet ) {
        super( TRACKED_MTYPES );
        clientSet_ = clientSet;
        clientMap_ = clientSet.getClientMap();
        opQueue_ = new OperationQueue();
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

        // Set up the client set to contain an entry for each registered
        // client.
        int nc = clientIds.length;
        TrackedClient[] clients = new TrackedClient[ nc ];
        for ( int ic = 0; ic < nc; ic++ ) {
            clients[ ic ] = new TrackedClient( clientIds[ ic ] );
        }
        clientSet_.setClients( clients );

        // Then get the metadata and subscriptions for each.
        for ( int ic = 0; ic < nc; ic++ ) {
            TrackedClient client = clients[ ic ];
            String id = client.getId();
            client.setMetadata( connection.getMetadata( id ) );
            client.setSubscriptions( connection.getSubscriptions( id ) );
            clientSet_.updateClient( client );
            opQueue_.apply( client );
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
            TrackedClient client = new TrackedClient( id );
            opQueue_.apply( client );
            clientSet_.addClient( client );
        }
        else if ( UNREGISTER_MTYPE.equals( mtype ) ) {
            performClientOperation( new ClientOperation( id, mtype ) {
                public void perform( TrackedClient client ) {
                    opQueue_.discard( client );
                    clientSet_.removeClient( client );
                }
            }, connection );
        }
        else if ( METADATA_MTYPE.equals( mtype ) ) {
            final Map meta = (Map) message.getParams().get( "metadata" );
            performClientOperation( new ClientOperation( id, mtype ) {
                public void perform( TrackedClient client ) {
                    client.setMetadata( meta );
                    clientSet_.updateClient( client );
                }
            }, connection );
        }
        else if ( SUBSCRIPTIONS_MTYPE.equals( mtype ) ) {
            final Map subs = (Map) message.getParams().get( "subscriptions" );
            performClientOperation( new ClientOperation( id, mtype ) {
                public void perform( TrackedClient client ) {
                    client.setSubscriptions( subs );
                    clientSet_.updateClient( client );
                }
            }, connection );
        }
        else {
            throw new IllegalArgumentException( "Shouldn't have received MType"
                                              + mtype );
        }
        return null;
    }

    /**
     * Performs an operation on a ClientOperation object.
     *
     * @param  op  client operation
     * @param  connection  hub connection
     */
    private void performClientOperation( ClientOperation op,
                                         HubConnection connection ) {
        String id = op.getId();

        // If the client is currently part of this tracker's data model,
        // we can peform the operation directly.
        TrackedClient client = (TrackedClient) clientMap_.get( id );
        if ( client != null ) {
            op.perform( client );
        }

        // If it's not, but it applies to this client itself, it's just 
        // because we haven't added ourself to the client list yet.
        // Queue it.
        else if ( id.equals( connection.getRegInfo().getSelfId() ) ) {
            opQueue_.add( op );
        }

        // Otherwise, the client is not yet known.  This is most likely 
        // because, in absence of any guarantee about message delivery order
        // within SAMP, a message which was sent between its registration
        // and its unregistration might still arrive either before its
        // registration event has arrived or after its unregistration event
        // has arrived.  In the hope that it is the former, we hang on to
        // this operation so that it can be peformed at some future date
        // when we actually have a client object we can apply it to.
        else {

            // If it's for this client, this is just because it hasn't added
            // itself to the client list yet.  Should get resolved very soon.
            if ( id.equals( connection.getRegInfo().getSelfId() ) ) {
                logger_.info( "Message " + op.getMType() + " arrived for self"
                            + " - holding till later" );
            }

            // Otherwise less certain, but we still hope.
            else {
                logger_.info( "No known client " + id + " for message "
                            + op.getMType() + " - holding till later" );
            }

            // Either way, queue it.
            opQueue_.add( op );
        }
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
            return SampUtils.toString( this );
        }
    }

    /**
     * Describes an operation to be performed on a TrackedClient object
     * which is already part of this tracker's model.
     */
    private static abstract class ClientOperation {
        private final String id_;
        private final String mtype_;
        private final long birthday_;

        /**
         * Constructor.
         *
         * @param  id  client public ID
         * @param  mtype  MType of the message which triggered this operation
         */
        ClientOperation( String id, String mtype ) {
            id_ = id;
            mtype_ = mtype;
            birthday_ = System.currentTimeMillis();
        }

        /**
         * Performs the instance-specific operation on a given client.
         *
         * @param  client  client
         */
        public abstract void perform( TrackedClient client );

        /**
         * Returns the client ID for the client this operation applies to.
         *
         * @return  client public ID
         */
        public String getId() {
            return id_;
        }

        /**
         * Returns the MType of the message which triggered this operation.
         *
         * @return  message MType
         */
        public String getMType() {
            return mtype_;
        }

        /**
         * Returns the creation time of this object.
         *
         * @return  <code>System.currentTimeMillis()</code> at construction
         */
        public long getBirthday() {
            return birthday_;
        }

        public String toString() {
            return "message " + mtype_ + " for client " + id_;
        }
    }

    /**
     * Data structure for holding ClientOperation objects which (may) need
     * to be applied in the future.
     * Operations are dumped here if they cannot be performed immediately
     * because the client in question is not (yet) known by this tracker.
     * The hope is that the client will register at some point in the future
     * and the pending operations can be applied then.  However, this may
     * never happen, so the queue maintains its own expiry system to throw
     * out old events.
     */
    private static class OperationQueue {
        private final Collection opList_;
        private Timer tidyTimer_;

        /**
         * Constructor.
         */
        OperationQueue() {
            opList_ = new ArrayList();
        }

        /**
         * Add a new client operation which may get the opportunity to be
         * performed some time in the future.
         *
         * @param  op   oeration to add
         */
        public synchronized void add( ClientOperation op ) {
            if ( tidyTimer_ == null ) {
                TimerTask tidy = new TimerTask() {
                    public void run() {
                        discardOld( QUEUE_TIME );
                    }
                };
                tidyTimer_ = new Timer( true );
                tidyTimer_.schedule( tidy, QUEUE_TIME, QUEUE_TIME );
            }
            opList_.add( op );
        }

        /**
         * Apply any pending operations to given client.
         * This client was presumably unavailable at the time such operations
         * were queued.
         *
         * @param  client    client to apply pending operations to
         */
        public synchronized void apply( TrackedClient client ) {
            String id = client.getId();
            for ( Iterator it = opList_.iterator(); it.hasNext(); ) {
                ClientOperation op = (ClientOperation) it.next();
                if ( op.getId().equals( id ) ) {
                    logger_.info( "Performing queued " + op );
                    op.perform( client );
                    it.remove();
                }
            }
        }

        /**
         * Discards any operations corresponding to a given client,
         * presumably because the client is about to disappear.
         *
         * @param  client  client to forget about
         */
        public synchronized void discard( TrackedClient client ) {
            String id = client.getId();
            for ( Iterator it = opList_.iterator(); it.hasNext(); ) {
                ClientOperation op = (ClientOperation) it.next();
                if ( op.getId().equals( id ) ) {
                    logger_.warning( "Discarding queued " + op );
                    it.remove();
                }
            }
        }

        /**
         * Throws away any pending operations which are older than a certain
         * age, presumably in the expectation that their client will never
         * register.
         *
         * @param   maxAge   oldest operations (in milliseconds) permitted to
         *          remain in the queue
         */
        public synchronized void discardOld( long maxAge ) {
            long now = System.currentTimeMillis();
            for ( Iterator it = opList_.iterator(); it.hasNext(); ) {
                ClientOperation op = (ClientOperation) it.next();
                if ( now - op.getBirthday() > maxAge ) {
                    logger_.warning( "Discarding queued " + op
                                   + " - client never showed up" );
                    it.remove();
                }
            }
        }

        /**
         * Removes all entries from this queue.
         */
        public synchronized void clear() {
            for ( Iterator it = opList_.iterator(); it.hasNext(); ) {
                ClientOperation op = (ClientOperation) it.next();
                logger_.warning( "Discarding queued " + op );
            }
            opList_.clear();
        }
    }
}
