package org.astrogrid.samp.hub;

import java.util.Map;

/**
 * Specifies restrictions on the message types that may be sent in
 * a particular context.
 * In general if null is used in place of a MessageRestriction object,
 * the understanding is that no restrictions apply.
 *
 * @author   Mark Taylor
 * @since    23 Nov 2011
 */
public interface MessageRestriction {

    /**
     * Indicates whether a message covered by a given MType subscription
     * may be sent.
     *
     * @param  mtype  the MType string to be sent
     * @param  subsInfo  the annotation map corresponding to the MType
     *                   subscription (the value from the Subscriptions map
     *                   corresponding to the <code>mtype</code> key)
     * @return  true if the message may be sent, false if it is blocked
     */
    boolean permitSend( String mtype, Map subsInfo );
}
