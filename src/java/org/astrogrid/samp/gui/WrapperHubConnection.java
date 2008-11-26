package org.astrogrid.samp.gui;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * HubConnection implementation which delegates all behaviour to a base
 * implementation.  Intended for subclassing.
 *
 * @author   Mark Taylor 
 * @since    24 Nov 2008
 */
class WrapperHubConnection implements HubConnection {
    private final HubConnection base_;

    public WrapperHubConnection( HubConnection base ) {
        base_ = base;
    }

    public RegInfo getRegInfo() {
        return base_.getRegInfo();
    }

    public void setCallable( CallableClient callable ) throws SampException {
        base_.setCallable( callable );
    }

    public void ping() throws SampException {
        base_.ping();
    }

    public void unregister() throws SampException {
        base_.ping();
    }

    public void declareMetadata( Map meta ) throws SampException {
        base_.declareMetadata( meta );
    }

    public Metadata getMetadata( String clientId ) throws SampException {
        return base_.getMetadata( clientId );
    }

    public void declareSubscriptions( Map subs ) throws SampException {
        base_.declareSubscriptions( subs );
    }

    public Subscriptions getSubscriptions( String clientId )
            throws SampException {
        return base_.getSubscriptions( clientId );
    }

    public String[] getRegisteredClients() throws SampException {
        return base_.getRegisteredClients();
    }

    public Map getSubscribedClients( String mtype ) throws SampException {
        return base_.getSubscribedClients( mtype );
    }

    public void notify( String recipientId, Map msg ) throws SampException {
        base_.notify( recipientId, msg );
    }

    public List notifyAll( Map msg ) throws SampException {
        return base_.notifyAll( msg );
    }

    public String call( String recipientId, String msgTag, Map msg )
            throws SampException {
        return base_.call( recipientId, msgTag, msg );
    }

    public Map callAll( String msgTag, Map msg ) throws SampException {
        return base_.callAll( msgTag, msg );
    }

    public Response callAndWait( String recipientId, Map msg, int timeout )
            throws SampException {
        return base_.callAndWait( recipientId, msg, timeout );
    }

    public void reply( String msgId, Map response ) throws SampException {
        base_.reply( msgId, response );
    }
}
