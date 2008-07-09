package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

class HubReceiver implements Receiver {

    private final HubService hub_;
    private final Object clientId_;
    private final MessageHandler[] handlers_;

    public HubReceiver( HubService hub, Object clientId ) {
        this( hub, clientId, createDefaultHandlers() );
    }

    private HubReceiver( HubService hub, Object clientId,
                         MessageHandler[] handlers ) {
        hub_ = hub;
        clientId_ = clientId;
        handlers_ = handlers;
    } 

    public void receiveCall( String senderId, String msgId, Map message ) 
            throws SampException {
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
        throw new SampException( "Unsubscribed MType " + mtype );
    }

    public void receiveNotification( String senderId, Map message )
            throws SampException {
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
        throw new SampException( "Unsubscribed MType " + mtype );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Map response ) throws SampException {
        throw new SampException( "Not expecting any responses" );
    }

    public Subscriptions getSubscriptions() {
        Subscriptions subs = new Subscriptions();
        for ( int i = 0; i < handlers_.length; i++ ) {
            subs.addMType( handlers_[ i ].getMType() );
        }
        return subs;
    }

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

    private static abstract class MessageHandler {
        private final String mtype_;

        MessageHandler( String mtype ) {
            mtype_ = mtype;
        }

        public String getMType() {
            return mtype_;
        }

        abstract Map processCall( String senderId, Message msg );
    }
}
