package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampException;

public interface HubService {
    void start();
    String getPassword();
    Map register( Object auth ) throws SampException;
    void unregister( Object id ) throws SampException;
    void setReceiver( Object id, Receiver receiver ) throws SampException;
    void declareMetadata( Object id, Map meta ) throws SampException;
    Map getMetadata( Object id, String clientId ) throws SampException;
    void declareSubscriptions( Object id, Map subs ) throws SampException;
    Map getSubscriptions( Object id, String clientId ) throws SampException;
    List getRegisteredClients( Object id ) throws SampException;
    Map getSubscribedClients( Object id, String mtype ) throws SampException;
    void notify( Object id, String recipientId, Map msg ) throws SampException;
    void notifyAll( Object id, Map msg ) throws SampException;
    String call( Object id, String recipientId, String msgTag, Map msg )
             throws SampException;;
    String callAll( Object id, String msgTag, Map msg ) throws SampException;
    Map callAndWait( Object id, String recipientId, Map msg, String timeout )
                     throws SampException;
    void reply( Object id, String msgId, Map response )
             throws SampException;
    void shutdown();
}
