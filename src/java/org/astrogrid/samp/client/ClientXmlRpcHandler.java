package org.astrogrid.samp.client;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampXmlRpcHandler;

/**
 * XmlRpcHandler implementation which passes Standard Profile-like XML-RPC
 * calls to one or more {@link CallableClient}s to provide client callbacks
 * from the hub.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class ClientXmlRpcHandler extends SampXmlRpcHandler {

    private final ClientActorImpl clientActor_;

    /**
     * Constructor.
     */
    public ClientXmlRpcHandler() {
        super( "samp.client", ClientActor.class, new ClientActorImpl() );
        clientActor_ = (ClientActorImpl) getActor();
    }

    /**
     * Adds a CallableClient object to this server.
     *
     * @param   privateKey  private key for the registered client on behalf
     *          of which the client will operate
     * @param   callable   callable client object
     */
    public void addClient( String privateKey, CallableClient callable ) {
        clientActor_.map_.put( privateKey, callable );
    }

    /**
     * Removes a CallableClient object from this server.
     *
     * @param  privateKey   key under which this client was added
     */
    public void removeClient( String privateKey ) {
        clientActor_.map_.remove( privateKey );
    }

    /**
     * Implementation of the {@link ClientActor} interface which does the
     * work for this class.
     * The correct CallableClient is determined from the private key,
     * and the work is then delegated to it.
     */
    private static class ClientActorImpl implements ClientActor {

        private final Map map_ = new HashMap();

        public void receiveNotification( String privateKey, String senderId,
                                         Map msg ) {
            getCallable( privateKey )
                .receiveNotification( senderId, Message.asMessage( msg ) );
        }

        public void receiveCall( String privateKey, String senderId,
                                 String msgId, Map msg ) {
            getCallable( privateKey )
                .receiveCall( senderId, msgId, Message.asMessage( msg ) );
        }

        public void receiveResponse( String privateKey, String responderId,
                                     String msgTag, Map response ) {
            getCallable( privateKey )
                .receiveResponse( responderId, msgTag,
                                  Response.asResponse( response ) );
        }

        /**
         * Returns the CallableClient corresponding to a given private key.
         *
         * @param   privateKey  private key for client
         * @return  callable client identified by privateKey
         * @throws  IllegalStateException  if <code>privateKey</code> is unknown
         */
        private CallableClient getCallable( String privateKey ) {
            Object cc = map_.get( privateKey );
            if ( cc instanceof CallableClient ) {
                return (CallableClient) cc;
            }
            else {
                throw new IllegalStateException( "Client is not listening" );
            }
        }
    }
}
