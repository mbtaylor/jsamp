package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Interface defining the work that the hub has to do.
 * This is independent of profile or transport, and just concerns
 * keeping track of clients and routing messages between them.
 *
 * <p>Most methods are declared to throw
 * {@link org.astrogrid.samp.client.SampException} which is a catch-all
 * to indicate that the request could not be fulfilled.
 * However, it's OK for any of these methods to throw
 * unchecked exceptions if that is more convenient for the implementation -
 * the service user should catch these if they occur.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface HubService {

    /**
     * Begin operation.  The {@link #register} method should not be
     * called until the hub has been started.
     */
    void start();

    /**
     * Returns the connection used by the hub itself to send and receive
     * messages.  This is the connection with a client ID given by
     * {@link org.astrogrid.samp.RegInfo#HUBID_KEY}.
     *
     * @return  hub service's own hub connection
     */
    HubConnection getServiceConnection();

    /**
     * Creates a new connection to this hub service, thereby initiating
     * a new registered client.
     *
     * @return   new hub connection representing registration of a new client
     */
    HubConnection register() throws SampException;

    /**
     * Forcibly disconnects a given client.
     * This call does three things:
     * <ol>
     * <li>sends a <code>samp.hub.disconnect</code> message to the
     *     client which is about to be ejected, if the client is
     *     subscribed to that MType</li>
     * <li>removes that client from this hub's client set so that any
     *     further communication attempts to or from it will fail</li>
     * <li>broadcasts a <code>samp.hub.unregister</code> message to all
     *     remaining clients indicating that the client has disappeared</li>
     * </ol>
     *
     * @param  clientId  public-id of client to eject
     * @param  reason    short text string indicating reason for ejection
     */
    void disconnect( String clientId, String reason ) throws SampException;

    /**
     * Indicates whether this hub service is currently open for operations.
     *
     * @return  true iff called between {@link #start} and {@link #shutdown}
     */
    boolean isHubRunning();

    /**
     * Tidies up any resources owned by this object.
     * Should be called when no longer required.
     */
    void shutdown();
}
