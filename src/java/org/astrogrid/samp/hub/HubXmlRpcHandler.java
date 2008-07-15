package org.astrogrid.samp.hub;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampXmlRpcHandler;
import org.astrogrid.samp.SampException;

/**
 * XmlRpcHandler implementation which passes Standard Profile-type XML-RPC
 * calls to a <code>HubService</code> to provide a SAMP hub service.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
class HubXmlRpcHandler extends SampXmlRpcHandler {

    /**
     * Constructor.
     *
     * @param   service  hub service
     * @param   secret  password required for client registration
     */
    public HubXmlRpcHandler( HubService service, String secret ) {
        super( "samp.hub", HubActor.class,
               new HubActorImpl( service, secret ) );
    }

    /**
     * Implementation of the {@link HubActor} interface which does 
     * the work for this class.
     * Apart from a few methods which have Standard-Profile-specific
     * aspects, the work is simply delegated to the HubService.
     */
    private static class HubActorImpl implements HubActor {
        private final HubService service_;
        private final String secret_;

        /**
         * Constructor.
         *
         * @param   service  hub service
         * @param   secret  password required for client registration
         */
        HubActorImpl( HubService service, String secret ) {
            service_ = service;
            secret_ = secret;
        }

        public void ping() {
        }

        public void ping( String privateKey ) {
        }

        public Map register( String secret ) throws SampException {
            if ( secret_.equals( secret ) ) {
                return service_.register();
            }
            else {
                throw new SampException( "Bad password" );
            }
        }

        public void unregister( String privateKey ) throws SampException {
            service_.unregister( privateKey );
        }

        public void setXmlrpcCallback( String privateKey, String surl )
                throws SampException {
            URL url;
            try {
                url = new URL( surl );
            }
            catch ( MalformedURLException e ) {
                throw new SampException( "Bad URL: " + surl );
            }
            service_.setReceiver( privateKey,
                                  new XmlRpcReceiver( privateKey, url ) );
        }

        public void declareMetadata( String privateKey, Map metadata )
                throws SampException {
            service_.declareMetadata( privateKey, metadata );
        }

        public Map getMetadata( String privateKey, String clientId ) 
                throws SampException {
            return service_.getMetadata( privateKey, clientId );
        }

        public void declareSubscriptions( String privateKey, Map subs ) 
                throws SampException {
            service_.declareSubscriptions( privateKey, subs );
        }

        public Map getSubscriptions( String privateKey, String clientId ) 
                throws SampException {
            return service_.getSubscriptions( privateKey, clientId );
        }

        public List getRegisteredClients( String privateKey ) 
                throws SampException {
            return service_.getRegisteredClients( privateKey );
        }

        public Map getSubscribedClients( String privateKey, String mtype ) 
                throws SampException {
            return service_.getSubscribedClients( privateKey, mtype );
        }

        public void notify( String privateKey, String recipientId, Map msg ) 
                throws SampException {
            service_.notify( privateKey, recipientId, msg );
        }

        public void notifyAll( String privateKey, Map msg ) 
                throws SampException {
            service_.notifyAll( privateKey, msg );
        }

        public String call( String privateKey, String recipientId,
                            String msgTag, Map msg ) throws SampException {
            return service_.call( privateKey, recipientId, msgTag, msg );
        }

        public String callAll( String privateKey, String msgTag, Map msg ) 
                throws SampException {
            return service_.callAll( privateKey, msgTag, msg );
        }

        public Map callAndWait( String privateKey, String recipientId, Map msg,
                                String timeout ) 
                throws SampException {
            return service_.callAndWait( privateKey, recipientId, msg,
                                         timeout );
        }

        public void reply( String privateKey, String msgId, Map response ) 
                throws SampException {
            service_.reply( privateKey, msgId, response );
        }
    }
}
