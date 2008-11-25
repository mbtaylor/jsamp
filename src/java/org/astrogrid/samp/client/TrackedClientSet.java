package org.astrogrid.samp.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Client;

/**
 * Collection of Client objects which can be notified and interrogated
 * about the clients which are currently registered.
 * Instances of this class are thread-safe.
 *
 * @author   Mark Taylor
 * @since    25 Nov 2008
 */
public class TrackedClientSet {

    private final Map clientMap_;
    private final Map clientMapView_;

    /**
     * Constructor.
     */
    public TrackedClientSet() {
        clientMap_ = new HashMap();
        clientMapView_ =
            Collections.synchronizedMap( Collections
                                        .unmodifiableMap( clientMap_ ) );
    }

    /**
     * Adds a client to this model.
     * Listeners are informed.  May be called from any thread.
     *
     * @param  client  client to add
     */
    public void addClient( Client client ) {
        synchronized ( clientMapView_ ) {
            clientMap_.put( client.getId(), client );
            clientMapView_.notifyAll();
        }
    }

    /**
     * Removes a client from this model.
     * Listeners are informed.  May be called from any thread.
     *
     * @param   client  client to remove
     */
    public synchronized void removeClient( Client client ) {
        Client c;
        synchronized ( clientMapView_ ) {
            c = (Client) clientMap_.remove( client.getId() );
            clientMapView_.notifyAll();
        }
        boolean removed = c != null;
        if ( ! removed ) {
            throw new IllegalArgumentException( "No such client " + client );
        }
        assert client.equals( c );
    }

    /**
     * Sets the contents of this model to a given list.
     * Listeners are informed.  May be called from any thread.
     *
     * @param  clients  current client list
     */
    public synchronized void setClients( Client[] clients ) {
        synchronized ( clientMapView_ ) {
            clientMap_.clear();
            for ( int ic = 0; ic < clients.length; ic++ ) {
                Client client = clients[ ic ];
                clientMap_.put( client.getId(), client );
            }
            clientMapView_.notifyAll();
        }
    }

    /**
     * Notifies listeners that a given client's attributes (may) have
     * changed.  May be called from any thread.
     *
     * @param  client  modified client
     */
    public void updateClient( Client client ) {
        synchronized ( clientMapView_ ) {
            clientMapView_.notifyAll();
        }
    }

    /**
     * Returns an unmodifiable Map representing the client list.
     * Keys are client IDs and values are {@link org.astrogrid.samp.Client}
     * objects.
     * <p>This map is {@link java.util.Collections#synchronizedMap synchronized}
     * which means that to iterate over any of its views 
     * you must synchronize on it.
     * When the map or any of its contents changes, it will receive a
     * {@link java.lang.Object#notifyAll}.
     *
     * @return   id -&gt; Client map
     */
    public Map getClientMap() {
        return clientMapView_;
    }
}
