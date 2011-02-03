package org.astrogrid.samp.hub;

import java.io.IOException;
import java.util.List;
import org.astrogrid.samp.client.ClientProfile;

/**
 * Defines a hub profile.
 * This profile allows registration and deregistration of clients to
 * a given provider of hub connections, using some profile-specific
 * transport and authentication arrangements.
 * Multiple profiles may be attached to a single connection supplier
 * at any time, and may be started and stopped independently of each other.
 * The connection supplier is typically a hub service running in the same
 * JVM, but may also be a client-side connection to a hub.
 * 
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public interface HubProfile {

    /**
     * Starts this hub's activity allowing access to a given supplier of
     * hub connections.
     *
     * @param   profile  object which can provide hub connections
     */
    void start( ClientProfile profile ) throws IOException;

    /**
     * Ends this profile's activity on behalf of the hub.
     * Any resources associated with the profile should be released.
     * This does not include messaging registered clients about profile
     * termination; that should be taken care of by the user of this profile.
     */
    void shutdown() throws IOException;
}
