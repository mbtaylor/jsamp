package org.astrogrid.samp.web;

/**
 * Defines which MTypes are permitted for use.
 *
 * @author   Mark Taylor
 * @since    25 Oct 2011
 */
public interface SubscriptionMask {

    /**
     * Indicates whether a given MType is legal in the present context.
     *
     * @param  mtype  MType string to test
     * @return   true iff <code>mtype</code> is allowed
     */
    boolean isMTypePermitted( String mtype );
}
