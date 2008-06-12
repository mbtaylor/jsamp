package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;

public class Client {

    private final String id_;
    private Metadata metadata_;
    private Subscriptions subscriptions_;

    public Client( String id ) {
        id_ = id;
    }

    public String getId() {
        return id_;
    }

    void setMetadata( Map metadata ) {
        metadata_ = Metadata.asMetadata( metadata );
    }

    public Metadata getMetadata() {
        return metadata_;
    }

    void setSubscriptions( Map subscriptions ) {
        subscriptions_ = Subscriptions.asSubscriptions( subscriptions );
    }

    public Subscriptions getSubscriptions() {
        return subscriptions_;
    }

    public boolean equals( Object o ) {
        if ( o instanceof Client ) {
            Client other = (Client) o;
            return other.id_.equals( this.id_ );
        }
        else {
            return false;
        }
    }

    public int hashCode() {
        return id_.hashCode();
    }
}
