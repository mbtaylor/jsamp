package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Interface defining the work that the hub has to do.
 * This is independent of profile or transport, and just concerns
 * keeping track of clients and routing messages between them.
 * This includes sending the various <code>samp.hub.event.*</code> messages
 * to subscribed clients at the appropriate times.
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
public interface HubService extends ClientProfile {

    /**
     * Begin operation.  The {@link #register} method should not be
     * called until the hub has been started.
     */
    void start();

    /**
     * Creates a new connection to this hub service, thereby initiating
     * a new registered client.
     *
     * @return   new hub connection representing registration of a new client
     */
    HubConnection register() throws SampException;

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
