package org.astrogrid.samp;

public interface Client {
    String getId();
    Metadata getMetadata();
    Subscriptions getSubscriptions();
}
