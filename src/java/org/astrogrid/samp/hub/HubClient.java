package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;

/**
 * Represents a client registered with a hub.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class HubClient implements Client {

    private final String publicId_;
    private final Object privateKey_;
    private volatile Subscriptions subscriptions_;
    private volatile Metadata metadata_;
    private volatile Receiver receiver_;

    /**
     * Constructor.
     *
     * @param  privateKey  client private key
     * @param  publicId    client public ID
     */
    public HubClient( Object privateKey, String publicId ) {
        privateKey_ = privateKey;
        publicId_ = publicId;
        subscriptions_ = new Subscriptions();
        metadata_ = new Metadata();
        receiver_ = new NoReceiver();
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
     * Returns this client's private key.
     *
     * @return private key
     */
    public Object getPrivateKey() {
        return privateKey_;
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
     * Sets the receiver which allows this client to receive callbacks.
     * If null is used, a no-op receiver is installed.
     *
     * @param  receiver  new receiver, or null
     */
    public void setReceiver( Receiver receiver ) {
        receiver_ = receiver == null ? new NoReceiver() : receiver;
    }

    /**
     * Returns the receiver which allows this client to receive callbacks.
     * It is never null.
     *
     * @return  receiver
     */
    public Receiver getReceiver() {
        return receiver_;
    }

    /**
     * Indicates whether this client is callable.
     *
     * @return  true  iff this client has a non-useless receiver installed
     */
    public boolean isCallable() {
        return ! ( receiver_ instanceof NoReceiver );
    }

    public String toString() {
        return SampUtils.toString( this );
    }

    /**
     * No-op receiver implementation.
     * Any attempt to call its methods results in an exception.
     */
    private class NoReceiver implements Receiver {
        public void receiveNotification( String senderId, Map message )
                throws HubServiceException {
            refuse();
        }
        public void receiveCall( String senderId, String msgId, Map message )
                throws HubServiceException {
            refuse();
        }
        public void receiveResponse( String responderId, String msgId,
                                     Map response )
                throws HubServiceException {
            refuse();
        }
        private void refuse() throws HubServiceException {
            throw new HubServiceException( "Client " + getId()
                                         + " is not callable" );
        }
    }
}
