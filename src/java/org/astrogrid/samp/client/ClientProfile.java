package org.astrogrid.samp.client;

import org.astrogrid.samp.SampException;

public interface ClientProfile {
    HubConnection createHubConnection() throws SampException;
}
