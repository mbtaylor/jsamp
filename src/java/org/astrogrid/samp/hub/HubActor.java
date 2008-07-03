package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampException;

/**
 * This interface is an implementation detail, but it has to be visible 
 * because it's used in another package.  Rats.
 */
public interface HubActor {
    void ping();
    void ping( String privateKey );
    Map register( String secret ) throws SampException;
    void unregister( String privateKey ) throws SampException;
    void setXmlrpcCallback( String privateKey, String url )
            throws SampException;
    void declareMetadata( String privateKey, Map metadata )
            throws SampException;
    Map getMetadata( String privateKey, String clientId )
            throws SampException;
    void declareSubscriptions( String privateKey, Map subs )
            throws SampException;
    Map getSubscriptions( String privateKey, String clientId )
            throws SampException;
    List getRegisteredClients( String privateKey )
            throws SampException;
    Map getSubscribedClients( String privateKey, String mtype )
            throws SampException;
    void notify( String privateKey, String recipientId, Map msg )
            throws SampException;
    void notifyAll( String privateKey, Map msg )
            throws SampException;
    String call( String privateKey, String recipientId, String msgTag, Map msg )
            throws SampException;
    String callAll( String privateKey, String msgTag, Map msg )
            throws SampException;
    Map callAndWait( String privateKey, String recipientId, Map msg,
                     String timeout )
            throws SampException;
    void reply( String privateKey, String msgId, Map response )
            throws SampException;
}
