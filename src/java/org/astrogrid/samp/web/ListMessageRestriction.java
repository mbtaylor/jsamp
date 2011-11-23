package org.astrogrid.samp.web;

import java.util.Map;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.hub.MessageRestriction;

/**
 * General purpose implementation of MessageRestriction.
 * It allows to either whitelist or blacklist a given list of MType
 * patterns, with the option for client subscriptions to override
 * this policy by setting the "x-samp.mostly-harmless" key in the
 * annotation map corresponding to a given MType subscription.
 *
 * @author   Mark Taylor
 * @since    23 Nov 2011
 */
public class ListMessageRestriction implements MessageRestriction {

    private final boolean allow_;
    private final boolean useSubsInfo_;
    private final Subscriptions subs_;

    /**
     * Default list of MType patterns returned by {@link #getSafeMTypes}.
     */
    public static String[] DEFAULT_SAFE_MTYPES = new String[] {
        "samp.app.*", "samp.msg.progress",
        "table.*", "image.*", "coord.*", "spectrum.*",
        "bibcode.*", "voresource.*",
    };

    /**
     * System property used to specify a default list of known safe MTypes,
     * which the {@link #DEFAULT} policy will permit.
     * The value is a comma-separated list of MType patterns.
     */
    public static final String SAFE_MTYPE_PROP = "jsamp.mtypes.safe";

    /**
     * Default MessageRestriction implementation.
     * The current implementation allows a list of MTypes believed to be safe,
     * as given by calling {@link #getSafeMTypes}, and blocks all others.
     * However, client subscriptions may override this by annotating their
     * subscriptions with an entry having the key
     * "<code>x-samp.mostly-harmless</code>".
     * If this has the value "1" the MType thus annotated is allowed,
     * and if it has the value "0" it is blocked, regardless of the safe list.
     */
    public static final MessageRestriction DEFAULT =
        new ListMessageRestriction( true, getSafeMTypes(), true );

    /**
     * MessageRestriction that permits all MTypes, except as overridden
     * by <code>x-samp.mostly-harmless</code> annotations.
     */
    public static final MessageRestriction ALLOW_ALL =
            new ListMessageRestriction( false, new String[ 0 ], true ) {
        public String toString() {
            return "ALLOW_ALL";
        }
    };

    /**
     * MessageRestriction that blocks all MTypes, except as overridden
     * by <code>x-samp.mostly-harmless</code> annotations.
     */
    public static final MessageRestriction DENY_ALL =
            new ListMessageRestriction( true, new String[ 0 ], true ) {
        public String toString() {
            return "DENY_ALL";
        }
    };

    /**
     * Constructor.
     *
     * @param   allow  whether the sense of the mtypes list is those
     *          that should be allowed (true) or blocked (false)
     * @param   mtypes  mtype patterns to be allowed or blocked
     * @param   useSubsInfo  if true, honour x-samp.mostly-harmless
     *          subscription annotations
     */
    public ListMessageRestriction( boolean allow, String[] mtypes,
                                   boolean useSubsInfo ) {
        allow_ = allow;
        useSubsInfo_ = useSubsInfo;
        subs_ = new Subscriptions();
        for ( int im = 0; im < mtypes.length; im++ ) {
            subs_.addMType( mtypes[ im ] );
        }
    }

    public boolean permitSend( String mtype, Map subsInfo ) {
        if ( useSubsInfo_ ) {
            Object markedHarmless = subsInfo.get( "samp.mostly-harmless" );
            if ( markedHarmless == null ) {
                markedHarmless = subsInfo.get( "x-samp.mostly-harmless" );
            }
            if ( "0".equals( markedHarmless ) ) {
                return false;
            }
            else if ( "1".equals( markedHarmless ) ) {
                return true;
            }
        }
        boolean knownSafe = ( ! allow_ ) ^ subs_.isSubscribed( mtype );
        return knownSafe;
    }

    public String toString() {
        StringBuffer sbuf = new StringBuffer()
            .append( allow_ ? "Allow" : "Deny" )
            .append( ' ' )
            .append( subs_.keySet() );
        if ( useSubsInfo_ ) {
            sbuf.append( "; " )
                .append( "honour (x-)samp.mostly-harmless" );
        }
        return sbuf.toString();
    }

    /**
     * Returns a list of MType patterns which are permitted by the DEFAULT
     * policy.  If the System Property {@value SAFE_MTYPE_PROP} exists,
     * its value is taken as a comma-separated list of known permitted MType
     * patterns.  Otherwise, the {@link #DEFAULT_SAFE_MTYPES} array is returned.
     *
     * @return  list of MTypes treated as harmless by default
     */
    public static String[] getSafeMTypes() {
        String safeMtypes = System.getProperty( SAFE_MTYPE_PROP );
        if ( safeMtypes == null ) {
            return DEFAULT_SAFE_MTYPES;
        }
        else {
            return safeMtypes.split( "," );
        }
    }
}
