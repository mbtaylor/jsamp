package org.astrogrid.samp.web;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class containing OriginAuthorizer implementations.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class OriginAuthorizers {

    /** OriginAuthorizer which always denies access. */
    public static final OriginAuthorizer FALSE =
        createFixedOriginAuthorizer( false, false );

    /** OriginAuthorizer which always permits access. */
    public static final OriginAuthorizer TRUE =
        createFixedOriginAuthorizer( true, true );

    /** OriginAuthorizer which queries the user via a popup dialogue. */
    public static final OriginAuthorizer SWING =
        createMemoryOriginAuthorizer(
            createLoggingOriginAuthorizer( new SwingOriginAuthorizer( null ),
                                           Level.INFO, Level.WARNING ) );
    private static final Logger logger_ =
        Logger.getLogger( OriginAuthorizers.class.getName() );

    /**
     * Private constructor prevents instantiation.
     */
    private OriginAuthorizers() {
    }

    /**
     * Returns an OriginAuthorizer with fixed responses, regardless of input.
     *
     * @param  individualPolicy  invariable response of
     *                           <code>authorize</code> method
     * @param  generalPolicy   invariable response of
     *                         <code>authorizeAll</code> method
     */
    public static OriginAuthorizer
                  createFixedOriginAuthorizer( final boolean individualPolicy,
                                               final boolean generalPolicy ) {
        return new OriginAuthorizer() {
            public boolean authorize( String origin ) {
                return individualPolicy;
            }
            public boolean authorizeAll() {
                return generalPolicy;
            }
        };
    }

    /**
     * Returns an OriginAuthorizer based on an existing one which logs
     * responses.
     *
     * @param  auth   base authorizer
     * @param  acceptLevel  level at which acceptances will be logged
     * @param  refuseLevel  level at which refusals will be logged
     */
    public static OriginAuthorizer
            createLoggingOriginAuthorizer( final OriginAuthorizer auth,
                                           final Level acceptLevel,
                                           final Level refuseLevel ) {
        return new OriginAuthorizer() {
            public synchronized boolean authorize( String origin ) {
                boolean accept = auth.authorize( origin );
                log( accept, "\"" + origin + "\"" );
                return accept;
            }
            public synchronized boolean authorizeAll() {
                boolean accept = auth.authorizeAll();
                log( accept, "all origins" );
                return accept;
            }
            private void log( boolean accept, String domain ) {
                if ( accept ) {
                    logger_.log( acceptLevel,
                                 "Accepted cross-origin requests for "
                               + domain );
                }
                else {
                    logger_.log( refuseLevel,
                                 "Rejected cross-origin requests for "
                               + domain );
                }
            }
        };
    }

    /**
     * Returns an OriginAuthorizer based on an existing one which caches
     * responses.
     *
     * @param  auth  base authorizer
     */
    public static OriginAuthorizer
                  createMemoryOriginAuthorizer( final OriginAuthorizer auth ) {
        return new OriginAuthorizer() {
            private final OriginAuthorizer baseAuth_ = auth;
            private final Set acceptedSet_ = new HashSet();
            private final Set refusedSet_ = new HashSet();
            private Boolean authorizeAll_;

            public synchronized boolean authorize( String origin ) {
                if ( refusedSet_.contains( origin ) ) {
                    return false;
                }
                else if ( acceptedSet_.contains( origin ) ) {
                    return true;
                }
                else {
                    boolean accepted = baseAuth_.authorize( origin );
                    ( accepted ? acceptedSet_ : refusedSet_ ).add( origin );
                    return accepted;
                }
            }

            public synchronized boolean authorizeAll() {
                if ( authorizeAll_ == null ) {
                    authorizeAll_ = Boolean.valueOf( baseAuth_.authorizeAll() );
                }
                return authorizeAll_.booleanValue();
            }
        };
    }
}
