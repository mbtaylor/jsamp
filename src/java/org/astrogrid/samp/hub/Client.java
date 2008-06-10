package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.CallableClient;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

public class Client {

    private final String publicId_;
    private final String privateKey_;
    private Subscriptions subscriptions_;
    private Metadata metadata_;
    private CallableClient callable_;

    public Client( String privateKey, String publicId ) {
        privateKey_ = privateKey;
        publicId_ = publicId;
        subscriptions_ = new Subscriptions();
        metadata_ = new Metadata();
        callable_ = new NoCallable();
    }

    public String getPrivateKey() {
        return privateKey_;
    }

    public String getPublicId() {
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

    public void setCallable( CallableClient callable ) {
        callable_ = callable == null ? new NoCallable() : callable;
    }

    public CallableClient getCallable() {
        return callable_;
    }

    public boolean isCallable() {
        return ! ( callable_ instanceof NoCallable );
    }

    private class NoCallable implements CallableClient {
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
            throw new SampException( "Client " + Client.this.toString()
                                   + " is not callable" );
        }
    }
}
