package org.astrogrid.samp.web;

import org.astrogrid.samp.Subscriptions;

/**
 * List-based implementation of SubscriptionMask.
 *
 * @author   Mark Taylor
 * @since    25 Oct 2011
 */
public class ListSubscriptionMask implements SubscriptionMask {

    private final boolean allow_;
    private final Subscriptions subs_;

    /** All MTypes permitted. */
    public static final SubscriptionMask ALL =
        new ListSubscriptionMask( false, new String[ 0 ] );

    /** No MTypes permitted. */
    public static final SubscriptionMask NONE =
        new ListSubscriptionMask( true, new String[ 0 ] );

    /** Permits a default list of MTypes - includes samp.*, table.*, etc. */
    public static final SubscriptionMask DEFAULT =
        new ListSubscriptionMask( true, new String[] {
            "samp.*", "table.*", "image.*", "coord.*", "spectrum.*",
            "bibcode.*", "voresource.*",
        } );

    /**
     * Constructor.
     *
     * @param  allow  true if supplying a list of allowed MTypes,
     *                false if supplying a list of denied MTypes
     * @param  mtypes array of MType patterns to allow/deny;
     *                wildcards are permitted with the usual subscription
     *                syntax and semantics
     */
    public ListSubscriptionMask( boolean allow, String[] mtypes ) {
        allow_ = allow;
        subs_ = new Subscriptions();
        for ( int im = 0; im < mtypes.length; im++ ) {
            subs_.addMType( mtypes[ im ] );
        }
    }

    public boolean isMTypePermitted( String mtype ) {
        return ( ! allow_ ) ^ subs_.isSubscribed( mtype );
    }

    public String toString() {
        return ( allow_ ? "Allow" : "Deny" ) + " " + subs_.keySet();
    }
}
