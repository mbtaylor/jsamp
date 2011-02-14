package org.astrogrid.samp.xmlrpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;

/**
 * SampXmlRpcHandler implementation which passes Standard Profile-like XML-RPC
 * calls to one or more {@link CallableClient}s to provide client callbacks
 * from the hub.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class ClientXmlRpcHandler extends ActorHandler {

    private final ClientActorImpl clientActor_;
    private static final Logger logger_ = 
        Logger.getLogger( ClientXmlRpcHandler.class.getName() );

    /**
     * Constructor.
     */
    public ClientXmlRpcHandler() {
        super( "samp.client.", ClientActor.class, new ClientActorImpl() );
        clientActor_ = (ClientActorImpl) getActor();
    }

    /**
     * Adds a CallableClient object to this server.
     *
     * @param   connection  hub connection for the registered client on behalf
     *          of which the client will operate
     * @param   callable   callable client object
     */
    public void addClient( HubConnection connection, CallableClient callable ) {
        clientActor_.entryMap_.put( connection.getRegInfo().getPrivateKey(),
                                    new Entry( connection, callable ) );
    }

    /**
     * Removes a CallableClient object from this server.
     *
     * @param  privateKey   hub connection for which this client was added
     */
    public void removeClient( HubConnection connection ) {
        clientActor_.entryMap_
                    .remove( connection.getRegInfo().getPrivateKey() );
    }

    protected Object invokeMethod( Method method, Object obj, Object[] args )
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke( obj, args );
    }

    /**
     * Implementation of the {@link ClientActor} interface which does the
     * work for this class.
     * The correct CallableClient is determined from the private key,
     * and the work is then delegated to it.
     */
    private static class ClientActorImpl implements ClientActor {

        private Map entryMap_ = new HashMap();  // String -> Entry

        public void receiveNotification( String privateKey,
                                         final String senderId, Map msg ) {
            Entry entry = getEntry( privateKey );
            final CallableClient callable = entry.callable_;
            final Message message = Message.asMessage( msg );
            final String label = "Notify " + senderId + " "
                               + message.getMType();
            new Thread( label ) {
                public void run() {
                    try {
                        callable.receiveNotification( senderId, message );
                    }
                    catch ( Throwable e ) {
                        logger_.log( Level.INFO, label + " error", e );
                    }
                }
            }.start();
        }

        public void receiveCall( String privateKey, final String senderId,
                                 final String msgId, Map msg )
                throws Exception {
            Entry entry = getEntry( privateKey );
            final CallableClient callable = entry.callable_;
            final HubConnection connection = entry.connection_;
            final Message message = Message.asMessage( msg );
            final String label = "Call " + senderId + " " + message.getMType();
            new Thread( label ) {
                public void run() {
                    try {
                        callable.receiveCall( senderId, msgId, message );
                    }
                    catch ( Throwable e ) {
                        try {
                            Response response =
                                Response
                               .createErrorResponse( new ErrInfo( e ) );
                            connection.reply( msgId, response );
                        }
                        catch ( Throwable e2 ) {
                            logger_.log( Level.INFO,
                                         label + " error replying", e2 );
                        }
                    }
                }
            }.start();
        }

        public void receiveResponse( String privateKey,
                                     final String responderId,
                                     final String msgTag, Map resp )
                 throws Exception {
            Entry entry = getEntry( privateKey );
            final CallableClient callable = entry.callable_;
            final Response response = Response.asResponse( resp );
            final String label = "Reply " + responderId;
            new Thread( label ) {
                public void run() {
                    try {
                        callable.receiveResponse( responderId, msgTag,
                                                  response );
                    }
                    catch ( Throwable e ) {
                        logger_.log( Level.INFO, label + " error replying", e );
                    }
                }
            }.start();
        }

        /**
         * Returns the CallableClient corresponding to a given private key.
         *
         * @param   privateKey  private key for client
         * @return  entry identified by privateKey
         * @throws  IllegalStateException  if <code>privateKey</code> is unknown
         */
        private Entry getEntry( String privateKey ) {
            Object ent = entryMap_.get( privateKey );
            if ( ent instanceof Entry ) {
                return (Entry) ent;
            }
            else {
                throw new IllegalStateException( "Client is not listening" );
            }
        }
    }

    /**
     * Utility class to aggregate information about a client.
     */
    private static class Entry {
        final HubConnection connection_;
        final CallableClient callable_;

        /**
         * Constructor.
         *
         * @param   connection  hub connection
         * @param   callable  callable client
         */
        Entry( HubConnection connection, CallableClient callable ) {
            connection_ = connection;
            callable_ = callable;
        }
    }
}
