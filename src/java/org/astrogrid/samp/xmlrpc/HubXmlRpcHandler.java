package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.KeyGenerator;

/**
 * SampXmlRpcHandler implementation which passes Standard Profile-type XML-RPC
 * calls to a <code>HubService</code> to provide a SAMP hub service.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
class HubXmlRpcHandler extends ActorHandler {

    /**
     * Constructor.
     *
     * @param   xClientFactory  XML-RPC client factory implementation
     * @param   service  hub service
     * @param   secret  password required for client registration
     */
    public HubXmlRpcHandler( SampXmlRpcClientFactory xClientFactory,
                             HubService service, String secret,
                             KeyGenerator keyGen ) {
        super( "samp.hub.", HubActor.class,
               new HubActorImpl( xClientFactory, service, secret, keyGen ) );
    }

    /**
     * Implementation of the {@link HubActor} interface which does 
     * the work for this class.
     * Apart from a few methods which have Standard-Profile-specific
     * aspects, the work is simply delegated to the HubService.
     */
    private static class HubActorImpl implements HubActor {
        private final SampXmlRpcClientFactory xClientFactory_;
        private final HubService service_;
        private final String secret_;
        private final KeyGenerator keyGen_;
        private final Map clientMap_;

        /**
         * Constructor.
         *
         * @param   xClientFactory  XML-RPC client factory implementation
         * @param   service  hub service
         * @param   secret  password required for client registration
         * @param   keyGen  generator for private keys
         */
        HubActorImpl( SampXmlRpcClientFactory xClientFactory,
                      HubService service, String secret, KeyGenerator keyGen ) {
            xClientFactory_ = xClientFactory;
            service_ = service;
            secret_ = secret;
            keyGen_ = keyGen;
            clientMap_ = Collections.synchronizedMap( new HashMap() );
        }

        public Map register( String secret ) throws SampException {
            if ( secret_.equals( secret ) ) {
                HubConnection connection = service_.register();
                String privateKey = keyGen_.next();
                RegInfo regInfo = connection.getRegInfo();
                regInfo.put( RegInfo.PRIVATEKEY_KEY, privateKey );
                clientMap_.put( privateKey, connection );
                return regInfo;
            }
            else {
                throw new SampException( "Bad password" );
            }
        }

        public void unregister( String privateKey ) throws SampException {
            HubConnection connection =
                (HubConnection) clientMap_.remove( privateKey );
            if ( connection == null ) {
                throw new SampException( "Unknown private key" );
            }
            else {
                connection.unregister();
            }
        }

        public void ping( String privateKey ) throws SampException {
            getConnection( privateKey ).ping();
        }

        public void setXmlrpcCallback( String privateKey, String surl )
                throws SampException {
            SampXmlRpcClient xClient;
            try {
                xClient = xClientFactory_.createClient( new URL( surl ) );
            }
            catch ( MalformedURLException e ) {
                throw new SampException( "Bad URL: " + surl, e );
            }
            catch ( IOException e ) {
                throw new SampException( "No connection: "
                                       + e.getMessage(), e );
            }
            getConnection( privateKey )
                .setCallable( new XmlRpcCallableClient( xClient, privateKey ) );
        }

        public void declareMetadata( String privateKey, Map metadata )
                throws SampException {
            getConnection( privateKey ).declareMetadata( metadata );
        }

        public Map getMetadata( String privateKey, String clientId ) 
                throws SampException {
            return getConnection( privateKey ).getMetadata( clientId );
        }

        public void declareSubscriptions( String privateKey, Map subs ) 
                throws SampException {
            getConnection( privateKey ).declareSubscriptions( subs );
        }

        public Map getSubscriptions( String privateKey, String clientId ) 
                throws SampException {
            return getConnection( privateKey ).getSubscriptions( clientId );
        }

        public List getRegisteredClients( String privateKey ) 
                throws SampException {
            return Arrays.asList( getConnection( privateKey )
                                 .getRegisteredClients() );
        }

        public Map getSubscribedClients( String privateKey, String mtype ) 
                throws SampException {
            return getConnection( privateKey ).getSubscribedClients( mtype );
        }

        public void notify( String privateKey, String recipientId, Map msg ) 
                throws SampException {
            getConnection( privateKey ).notify( recipientId, msg );
        }

        public List notifyAll( String privateKey, Map msg ) 
                throws SampException {
            return getConnection( privateKey ).notifyAll( msg );
        }

        public String call( String privateKey, String recipientId,
                            String msgTag, Map msg )
                throws SampException {
            return getConnection( privateKey ).call( recipientId, msgTag, msg );
        }

        public Map callAll( String privateKey, String msgTag, Map msg ) 
                throws SampException {
            return getConnection( privateKey ).callAll( msgTag, msg );
        }

        public Map callAndWait( String privateKey, String recipientId, Map msg,
                                String timeoutStr ) 
                throws SampException {
            int timeout;
            try {
                timeout = SampUtils.decodeInt( timeoutStr );
            }
            catch ( Exception e ) {
                throw new SampException( "Bad timeout format"
                                       + " (should be SAMP int)", e );
            }
            return getConnection( privateKey )
                  .callAndWait( recipientId, msg, timeout );
        }

        public void reply( String privateKey, String msgId, Map response ) 
                throws SampException {
            getConnection( privateKey ).reply( msgId, response );
        }

        public void ping() throws SampException {
            if ( ! service_.isRunning() ) {
                throw new SampException( "Hub is stopped" );
            }
        }

        /**
         * Returns the HubConnection associated with a private key used
         * by this hub actor.
         *
         * @param  privateKey  private key
         * @return  connection for <code>privateKey</code>
         */
        private HubConnection getConnection( String privateKey )
                throws SampException {
            HubConnection connection =
                (HubConnection) clientMap_.get( privateKey );
            if ( connection == null ) {
                throw new SampException( "Unknown private key" );
            }
            else {
                return connection;
            }
        }
    }
}
