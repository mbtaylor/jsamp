package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampException;

public class WrapperHubService implements HubService {

    private final HubService base_;

    public WrapperHubService( HubService base ) {
        base_ = base;
    }

    public String getPassword() {
        return base_.getPassword();
    }

    public Map register( Object id ) throws SampException {
        return base_.register( id );
    }

    public void unregister( Object id ) throws SampException {
        base_.unregister( id );
    }

    public void declareMetadata( Object id, Map meta ) throws SampException {
        base_.declareMetadata( id, meta );
    }

    public Map getMetadata( Object id, String clientId ) throws SampException {
        return base_.getMetadata( id, clientId );
    }

    public void declareSubscriptions( Object id, Map subs )
            throws SampException {
        base_.declareSubscriptions( id, subs );
    }

    public Map getSubscriptions( Object id, String clientId )
            throws SampException {
        return base_.getSubscriptions( id, clientId );
    }

    public List getRegisteredClients( Object id ) throws SampException {
        return base_.getRegisteredClients( id );
    }

    public Map getSubscribedClients( Object id, String mtype )
            throws SampException {
        return base_.getSubscribedClients( id, mtype );
    }

    public void notify( Object id, String recipientId, Map msg )
            throws SampException {
        base_.notify( id, recipientId, msg );
    }

    public void notifyAll( Object id, Map msg ) throws SampException {
        base_.notifyAll( id, msg );
    }

    public String call( Object id, String recipientId, String msgTag, Map msg )
            throws SampException {
        return base_.call( id, recipientId, msgTag, msg );
    }

    public String callAll( Object id, String msgTag, Map msg )
            throws SampException {
        return base_.callAll( id, msgTag, msg );
    }

    public Map callAndWait( Object id, String recipientId, Map msg,
                            String timeout )
            throws SampException {
        return base_.callAndWait( id, recipientId, msg, timeout );
    }

    public void reply( Object id, String msgId, Map response )
            throws SampException {
        base_.reply( id, msgId, response );
    }

    public void shutdown() {
        base_.shutdown();
    }

    public HubService getBase() {
        return base_;
    }

    public void start() {
        base_.start();
    }

    protected HubClient createHubClient() {
        if ( base_ instanceof BasicHubService ) {
            return ((BasicHubService) base_).createHubClient();
        }
        else if ( base_ instanceof WrapperHubService ) {
            return ((WrapperHubService) base_).createHubClient();
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    protected ClientSet createClientSet() {
        if ( base_ instanceof BasicHubService ) {
            return ((BasicHubService) base_).createClientSet();
        }
        else if ( base_ instanceof WrapperHubService ) {
            return ((WrapperHubService) base_).createClientSet();
        }
        else {
            throw new UnsupportedOperationException();
        }
    }
}
