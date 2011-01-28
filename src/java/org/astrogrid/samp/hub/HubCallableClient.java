package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * CallableClient implementation used by the hub client.
 * This isn't exactly essential, but it enables the hub client 
 * (the client which represents the hub itself) to subscribe to some MTypes.
 * Possibly useful for testing purposes etc.
 *
 * @author   Mark Taylor
 * @since    28 Jan 2011
 */
class HubCallableClient implements CallableClient {

    private final HubClient client_;
    private final HubConnection connection_;
    private final MessageHandler[] handlers_;

    /**
     * Constructs a HubCallableClient with a default set of handlers.
     *
     * @param  client  hub client object
     * @param  connection  connection to hub service
     */
    public HubCallableClient( HubClient client, HubConnection connection ) {
        this( client, connection, createDefaultHandlers() );
    }

    /**
     * Constructs a HubCallableClient with a given set of handlers.
     *
     * @param  client  hub client object
     * @param  connection  connection to hub service
     * @param  handlers  array of message handlers
     */
    private HubCallableClient( HubClient client, HubConnection connection,
                               MessageHandler[] handlers ) {
        client_ = client;
        connection_ = connection;
        handlers_ = handlers;
    } 

    public void receiveCall( String senderId, String msgId, Message msg ) 
            throws SampException {
        msg.check();
        String mtype = msg.getMType();
        for ( int i = 0; i < handlers_.length; i++ ) {
            MessageHandler handler = handlers_[ i ];
            if ( mtype.equals( handler.getMType() ) ) {
                Response response;
                try {
                    Map result = handler.processCall( senderId, msg );
                    result = result == null ? new HashMap() : result;
                    response = Response.createSuccessResponse( result );
                }
                catch ( Exception e ) {
                    response = Response.createErrorResponse( new ErrInfo( e ) );
                }
                connection_.reply( msgId, response );
                return;
            }
        }
        throw new SampException( "Unsubscribed MType " + mtype );
    }

    public void receiveNotification( String senderId, Message msg )
            throws SampException {
        msg.check();
        String mtype = msg.getMType();
        for ( int i = 0; i < handlers_.length; i++ ) {
            MessageHandler handler = handlers_[ i ];
            if ( mtype.equals( handler.getMType() ) ) {
                handler.processCall( senderId, msg );
                return;
            }
        }
        throw new SampException( "Unsubscribed MType " + mtype );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Response response ) throws SampException {
    }

    /**
     * Returns the subscriptions corresponding to the messages that this
     * receiver can deal with.
     *
     * @return  subscriptions list
     */
    public Subscriptions getSubscriptions() {
        Subscriptions subs = new Subscriptions();
        for ( int i = 0; i < handlers_.length; i++ ) {
            subs.addMType( handlers_[ i ].getMType() );
        }
        return subs;
    }

    /**
     * Constructs a default list of MessageHandlers to use for a HubReceiver.
     *
     * @return   default handler list
     */
    private static MessageHandler[] createDefaultHandlers() {
        return new MessageHandler[] {
            new MessageHandler( "samp.app.ping" ) {
                Map processCall( String senderId, Message msg ) {
                    return new HashMap();
                }
            },
//          new MessageHandler( "app.echo" ) {
//              Map processCall( String senderId, Message msg ) {
//                  return msg.getParams();
//              }
//          },
        };
    }

    /**
     * Abstract class which encapsulates processing of a given MType.
     */
    private static abstract class MessageHandler {
        private final String mtype_;

        /**
         * Constructor.
         *
         * @param  mtype  MType
         */
        MessageHandler( String mtype ) {
            mtype_ = mtype;
        }

        /**
         * Returns the MType processed by this handler.
         *
         * @return  mtype
         */
        public String getMType() {
            return mtype_;
        }

        /**
         * Does the work for processing a message with the MType of this
         * handler.
         *
         * @param   senderId  sender public id
         * @param   msg   message map with the correct MType
         * @return  <code>samp.result</code> part of a SAMP-encoded response map
         */
        abstract Map processCall( String senderId, Message msg );
    }
}
