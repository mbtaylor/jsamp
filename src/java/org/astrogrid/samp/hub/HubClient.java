package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public class HubClient implements Client {

    private final String publicId_;
    private final String privateKey_;
    private Subscriptions subscriptions_;
    private Metadata metadata_;
    private Receiver receiver_;

    public HubClient( String privateKey, String publicId ) {
        privateKey_ = privateKey;
        publicId_ = publicId;
        subscriptions_ = new Subscriptions();
        metadata_ = new Metadata();
        receiver_ = new NoReceiver();
    }

    public String getPrivateKey() {
        return privateKey_;
    }

    public String getId() {
        return publicId_;
    }

    public void setMetadata( Map meta ) {
        metadata_ = new Metadata( meta );
    }

    public Metadata getMetadata() {
        return metadata_;
    }

    public void setSubscriptions( Map subs ) {
        subscriptions_ = Subscriptions.asSubscriptions( subs );
    }

    public Subscriptions getSubscriptions() {
        return subscriptions_;
    }

    public boolean isSubscribed( String mtype ) {
        return isCallable() && subscriptions_.isSubscribed( mtype );
    }

    public Map getSubscription( String mtype ) {
        return isCallable() ? subscriptions_.getSubscription( mtype )
                            : null;
    }

    public void setReceiver( Receiver receiver ) {
        receiver_ = receiver == null ? new NoReceiver() : receiver;
    }

    public Receiver getReceiver() {
        return receiver_;
    }

    public boolean isCallable() {
        return ! ( receiver_ instanceof NoReceiver );
    }

    private class NoReceiver implements Receiver {
        public void receiveNotification( String senderId, Map message )
                throws SampException {
            refuse();
        }
        public void receiveCall( String senderId, String msgId, Map message )
                throws SampException {
            refuse();
        }
        public void receiveResponse( String responderId, String msgId,
                                     Map response )
                throws SampException {
            refuse();
        }
        private void refuse() throws SampException {
            throw new SampException( "Client " + HubClient.this.toString()
                                   + " is not callable" );
        }
    }
}
