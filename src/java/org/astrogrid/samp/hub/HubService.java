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
     * Creates a new connection to this hub service, thereby initiating
     * a new registered client.
     *
     * <p>It is the responsibility of the returned connection, not the
     * user of that connection, to broadcast the various 
     * <code>samp.hub.event.*</code> notifications at the appropriate times.
     *
     * <p>Most of the <code>HubConnection</code> methods are  declared to
     * throw <code>SampException</code>, however, implementations may
     * throw unchecked exceptions if that is more convenient;
     * users of the connection should be prepared to catch these if
     * they occur.
     *
     * @param   profileToken  identifier for the profile acting as gatekeeper
     *          for this connection
     * @return   new hub connection representing registration of a new client
     */
    HubConnection register( ProfileToken profileToken ) throws SampException;

    /**
     * Declares that any connections created by a previous call of
     * {@link #register}
     * with a particular <code>profileToken</code> should be
     * forcibly terminated.  This causes any necessary hub events to
     * be broadcast.
     *
     * @param  profileToken   previous argument to <code>register</code>
     */
    void disconnectAll( ProfileToken profileToken );

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
