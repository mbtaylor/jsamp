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
 * A profile should be able to undergo multiple start/stop cycles.
 * 
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public interface HubProfile extends ProfileToken {

    /**
     * Starts this profile's activity allowing access to a given supplier of
     * hub connections.
     *
     * @param   profile  object which can provide hub connections
     */
    void start( ClientProfile profile ) throws IOException;

    /**
     * Indicates whether this profile is currently running.
     *
     * @return  true iff profile is running
     */
    boolean isRunning();

    /**
     * Ends this profile's activity on behalf of the hub.
     * Any resources associated with the profile should be released.
     * This does not include messaging registered clients about profile
     * termination; that should be taken care of by the user of this profile.
     */
    void stop() throws IOException;

    /**
     * Returns the name of this profile.
     *
     * @return  profile name, usually one word
     */
    public String getProfileName();
}
