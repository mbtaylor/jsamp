package org.astrogrid.samp;

/**
 * Describes an application registered with a SAMP hub.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public interface Client {

    /**
     * Returns the public identifier for this client.
     *
     * @return  public id
     */
    String getId();

    /**
     * Returns the currently declared metadata for this client, if any.
     *
     * @return  metadata object; may be null
     */
    Metadata getMetadata();

    /**
     * Returns the currently declared subscriptions for this client, if any.
     *
     * @return   subscriptions object; may be null
     */
    Subscriptions getSubscriptions();
}
