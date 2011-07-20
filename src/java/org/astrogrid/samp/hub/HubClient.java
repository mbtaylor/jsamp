package org.astrogrid.samp.hub;

import java.util.Map;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.SampException;

/**
 * Represents a client registered with a hub.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class HubClient implements Client {

    private final String publicId_;
    private final ProfileToken profileToken_;
    private volatile Subscriptions subscriptions_;
    private volatile Metadata metadata_;
    private volatile CallableClient callable_;

    /**
     * Constructor.
     *
     * @param  publicId    client public ID
     * @param  profileToken  identifier for the source of the hub connection
     */
    public HubClient( String publicId, ProfileToken profileToken ) {
        publicId_ = publicId;
        profileToken_ = profileToken;
        subscriptions_ = new Subscriptions();
        metadata_ = new Metadata();
        callable_ = new NoCallableClient();
    }

    public String getId() {
        return publicId_;
    }

    public Metadata getMetadata() {
        return metadata_;
    }

    public Subscriptions getSubscriptions() {
        return subscriptions_;
    }

    /**
     * Returns a token identifying the source of this client's connection
     * to the hub.
     *
     * @return  profile token
     */
    public ProfileToken getProfileToken() {
        return profileToken_;
    }

    /**
     * Sets this client's metadata map.
     *
     * @param  meta  metadata map
     */
    public void setMetadata( Map meta ) {
        metadata_ = new Metadata( meta );
    }

    /**
     * Sets this client's subscriptions list.
     *
     * @param  subs  subscriptions map
     */
    public void setSubscriptions( Map subs ) {
        subscriptions_ = Subscriptions.asSubscriptions( subs );
    }

    /**
     * Indicates whether this client is subscribed to a given MType.
     *
     * @param  mtype  MType
     * @return  true iff subscribed to MType
     */
    public boolean isSubscribed( String mtype ) {
        return isCallable() && subscriptions_.isSubscribed( mtype );
    }

    /**
     * Returns the subscription information for a given MType for this client.
     *
     * @param   mtype   MType
     * @return  subscriptions map value for key <code>mtype</code>,
     *          or null if not subscribed
     */
    public Map getSubscription( String mtype ) {
        return isCallable() ? subscriptions_.getSubscription( mtype )
                            : null;
    }

    /**
     * Sets the callable object which allows this client to receive
     * callbacks.  If null is used, a no-op callable object is installed.
     *
     * @param  callable  new callable interface, or null
     */
    public void setCallable( CallableClient callable ) {
        callable_ = callable == null ? new NoCallableClient() : callable;
    }

    /**
     * Returns the callable object which allows this client to receive
     * callbacks.  It is never null.
     *
     * @return  callable object
     */
    public CallableClient getCallable() {
        return callable_;
    }

    /**
     * Indicates whether this client is callable.
     *
     * @return  true iff this client has a non-useless callback handler
     *          installed
     */
    public boolean isCallable() {
        return ! ( callable_ instanceof NoCallableClient );
    }

    public String toString() {
        return SampUtils.toString( this );
    }

    /**
     * No-op callback handler implementation.
     * Any attempt to call its methods results in an exception.
     */
    private class NoCallableClient implements CallableClient {
        public void receiveNotification( String senderId, Message message )
                throws SampException {
            refuse();
        }
        public void receiveCall( String senderId, String msgId,
                                 Message message )
                throws SampException {
            refuse();
        }
        public void receiveResponse( String responderId, String msgId,
                                     Response response )
                throws SampException {
            refuse();
        }
        private void refuse() throws SampException {
            throw new SampException( "Client " + getId() + " is not callable" );
        }
    }
}
