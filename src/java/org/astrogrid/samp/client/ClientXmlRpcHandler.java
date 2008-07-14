package org.astrogrid.samp.client;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampXmlRpcHandler;

class ClientXmlRpcHandler extends SampXmlRpcHandler {

    private final ClientActorImpl clientActor_;

    public ClientXmlRpcHandler() {
        super( "samp.client", ClientActor.class, new ClientActorImpl() );
        clientActor_ = (ClientActorImpl) getActor();
    }

    public void addClient( String privateKey, CallableClient callable ) {
        clientActor_.map_.put( privateKey, callable );
    }

    public void removeClient( String privateKey ) {
        clientActor_.map_.remove( privateKey );
    }

    private static class ClientActorImpl implements ClientActor {

        private final Map map_ = new HashMap();

        public void receiveNotification( String privateKey, String senderId,
                                         Map msg )
                throws SampException {
            getCallable( privateKey )
                .receiveNotification( senderId, Message.asMessage( msg ) );
        }

        public void receiveCall( String privateKey, String senderId,
                                 String msgId, Map msg )
                throws SampException {
            getCallable( privateKey )
                .receiveCall( senderId, msgId, Message.asMessage( msg ) );
        }

        public void receiveResponse( String privateKey, String responderId,
                                     String msgTag, Map response )
                throws SampException {
            getCallable( privateKey )
                .receiveResponse( responderId, msgTag,
                                  Response.asResponse( response ) );
        }

        private CallableClient getCallable( String privateKey ) 
                throws SampException {
            Object cc = map_.get( privateKey );
            if ( cc instanceof CallableClient ) {
                return (CallableClient) cc;
            }
            else {
                throw new SampException( "Client is not listening" );
            }
        }
    }
}
