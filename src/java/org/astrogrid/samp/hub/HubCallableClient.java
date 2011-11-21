package org.astrogrid.samp.hub;

import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.AbstractMessageHandler;
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

    private final HubConnection connection_;
    private final AbstractMessageHandler[] handlers_;

    /**
     * Constructs a HubCallableClient with a given set of handlers.
     *
     * @param  connection  connection to hub service
     * @param  handlers  array of message handlers
     */
    public HubCallableClient( HubConnection connection,
                              AbstractMessageHandler[] handlers ) {
        connection_ = connection;
        handlers_ = handlers;
    } 

    public void receiveCall( String senderId, String msgId, Message msg ) 
            throws SampException {
        msg.check();
        getHandler( msg.getMType() )
           .receiveCall( connection_, senderId, msgId, msg );
    }

    public void receiveNotification( String senderId, Message msg )
            throws SampException {
        msg.check();
        getHandler( msg.getMType() )
           .receiveNotification( connection_, senderId, msg );
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
            subs.putAll( handlers_[ i ].getSubscriptions() );
        }
        return subs;
    }

    /**
     * Returns a handler owned by this callable client which can handle
     * a given MType.  If more than one applies, the first one encountered
     * is returned.
     *
     * @param  mtype   MType to handle
     * @return  handler for <code>mtype</code>
     * @throws  SampException  if no suitable handler exists
     */
    private AbstractMessageHandler getHandler( String mtype )
            throws SampException {
        for ( int i = 0; i < handlers_.length; i++ ) {
            AbstractMessageHandler handler = handlers_[ i ];
            if ( Subscriptions.asSubscriptions( handler.getSubscriptions() )
                              .isSubscribed( mtype ) ) {
                return handler;
            }
        }
        throw new SampException( "Not subscribed to " + mtype );
    }
}
