package org.astrogrid.samp.client;

/**
 * Defines Profile-specific aspects of the SAMP client implementation.
 * The usual way for clients to obtain an instance of this class is by 
 * using {@link DefaultClientProfile#getProfile}.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface ClientProfile {

    /**
     * Attempts to register with a SAMP hub and return a corresponding
     * connection object.  Some profile-specific hub discovery mechanism
     * is used to locate the hub.
     * If no hub is running, null will normally be returned.
     *
     * @return   hub connection representing a new registration, or null
     * @throws   SampException  in case of some unexpected error
     */
    HubConnection register() throws SampException;

    /**
     * Indicates whether a hub contactable by this profile appears to be
     * running.  This method is intended to provide a best guess and
     * to execute reasonably quickly.  An appropriate implementation for
     * standard profile might be to check the existence of the lockfile,
     * but not to attempt contact or registration with the hub.
     *
     * @return  true iff it looks like a hub is running
     */
    boolean isHubRunning();
}
