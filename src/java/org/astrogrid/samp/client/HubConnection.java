package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public interface HubConnection {
    RegInfo getRegInfo();
    void setCallable( CallableClient callable ) throws SampException;
    void ping() throws SampException;
    void unregister() throws SampException;
    void declareMetadata( Map meta ) throws SampException;
    Metadata getMetadata( String clientId ) throws SampException;
    void declareSubscriptions( Map subs ) throws SampException;
    Subscriptions getSubscriptions( String clientId ) throws SampException;
    String[] getRegisteredClients() throws SampException;
    Map getSubscribedClients( String mtype ) throws SampException;
    void notify( String clientId, Map msg ) throws SampException;
    void notifyAll( Map msg ) throws SampException;
    String call( String recipientId, String msgTag, Map msg )
        throws SampException;
    String callAll( String msgTag, Map msg ) throws SampException;
    Response callAndWait( String recipientId, Map msg, int timeout )
        throws SampException;
    void reply( String msgId, Map response ) throws SampException;
}
