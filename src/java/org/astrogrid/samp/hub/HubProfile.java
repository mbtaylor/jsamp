package org.astrogrid.samp.hub;

import java.io.IOException;
import java.util.List;

/**
 * Defines a hub profile.
 * This profile allows registration and deregistration of clients to
 * a given HubService, using some profile-specific transport and
 * authentication arrangements.  Multiple profiles may be attached to a single
 * HubService at any time, and may be started and stopped independently of
 * each other.
 * 
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public interface HubProfile {

    /**
     * Starts this hub's activity allowing access to a given running
     * hub service.
     *
     * @param   service  object providing hub services
     */
    void start( HubService service ) throws IOException;

    /**
     * Ends this profile's activity on behalf of the hub.
     * Any resources associated with the profile should be released.
     */
    void shutdown() throws IOException;
}
