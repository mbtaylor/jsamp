package org.astrogrid.samp.hub;

import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * HubService that provides hub functionality by accessing an existing
 * hub service.  The existing hub service is defined by a supplied
 * ClientProfile object.
 *
 * @author   Mark Taylor
 * @since    1 Feb 2011
 */
public class FacadeHubService implements HubService {

    private final ClientProfile profile_;

    /**
     * Constructor.
     *
     * @param   profile  defines the hub connection factory on which this
     *          service is based
     */
    public FacadeHubService( ClientProfile profile ) {
        profile_ = profile;
    }

    public boolean isHubRunning() {
        return profile_.isHubRunning();
    }

    public HubConnection register() throws SampException {
        return profile_.register();
    }

    /**
     * No-op.
     */
    public void start() {
    }

    /**
     * No-op.
     */
    public void shutdown() {
    }
}
