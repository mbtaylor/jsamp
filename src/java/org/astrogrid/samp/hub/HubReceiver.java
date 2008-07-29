package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;

/**
 * Receiver implementation used by the hub client.
 * This isn't exactly essential, but it enables the hub client 
 * (the client which represents the hub itself) to subscribe to some MTypes.
 * Possibly useful for testing purposes etc.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
class HubReceiver implements Receiver {

    private final HubService hub_;
    private final Object clientId_;
    private final MessageHandler[] handlers_;

    /**
     * Constructs a HubReceiver with a default set of handlers.
     *
     * @param  hub  hub service object
     * @param  clientId  public id for hub client
     */
    public HubReceiver( HubService hub, Object clientId ) {
        this( hub, clientId, createDefaultHandlers() );
    }

    /**
     * Constructs a HubReceiver with a given set of handlers.
     *
     * @param  hub  hub service object
     * @param  clientId  public id for hub client
     * @param  handlers  array of message handlers
     */
    private HubReceiver( HubService hub, Object clientId,
                        MessageHandler[] handlers ) {
        hub_ = hub;
        clientId_ = clientId;
        handlers_ = handlers;
    } 

    public void receiveCall( String senderId, String msgId, Map message ) 
            throws HubServiceException {
        Message msg = Message.asMessage( message );
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
                hub_.reply( clientId_, msgId, response );
                return;
            }
        }
        throw new HubServiceException( "Unsubscribed MType " + mtype );
    }

    public void receiveNotification( String senderId, Map message )
            throws HubServiceException {
        Message msg = Message.asMessage( message );
        msg.check();
        String mtype = msg.getMType();
        for ( int i = 0; i < handlers_.length; i++ ) {
            MessageHandler handler = handlers_[ i ];
            if ( mtype.equals( handler.getMType() ) ) {
                handler.processCall( senderId, msg );
                return;
            }
        }
        throw new HubServiceException( "Unsubscribed MType " + mtype );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Map response ) throws HubServiceException {
        throw new HubServiceException( "Not expecting any responses" );
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
