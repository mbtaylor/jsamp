package org.astrogrid.samp.web;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * Utility class containing ClientAuthorizer implementations.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class ClientAuthorizers {

    /**
     * Authorizer which always denies access,
     * with INFO logging either way.
     */
    public static final ClientAuthorizer FALSE =
        createLoggingClientAuthorizer( createFixedClientAuthorizer( false ),
                                       Level.INFO, Level.INFO );

    /**
     * Authorizer which always permits access,
     * with WARNING logging either way.
     */
    public static final ClientAuthorizer TRUE =
        createLoggingClientAuthorizer( createFixedClientAuthorizer( true ),
                                       Level.WARNING, Level.WARNING );

    /**
     * Authorizer which queries the user via a popup dialogue,
     * with INFO logging either way.
     */
    private static ClientAuthorizer swingAuth_;

    private static final Logger logger_ =
        Logger.getLogger( ClientAuthorizers.class.getName() );

    private ClientAuthorizers() {
    }

    /**
     * Returns a new authorizer instance which always produces the same
     * authorization status.
     *
     * @param  policy  true for accept, false for deny
     * @return   new authorizer
     */
    public static ClientAuthorizer
                  createFixedClientAuthorizer( final boolean policy ) {
        if ( policy ) {
            return new ClientAuthorizer() {
                public void authorize( HttpServer.Request request,
                                       Map securityMap ) {
                }
            };
        }
        else {
            return new ClientAuthorizer() {
                public void authorize( HttpServer.Request request,
                                       Map securityMap )
                         throws SampException {
                     throw new SampException( "All registration requests "
                                            + "unconditionally denied" );
                }
            };
        }
    }

    /**
     * Returns a new authorizer instance based on an existing one which
     * logs authorization results through the logging system.
     *
     * @param  auth  base authorizer
     * @param  acceptLevel  logging level at which auth acceptances are logged
     * @param  refuseLevel  logging level at which auth refusals are logged
     * @return   new authorizer
     */
    public static ClientAuthorizer
            createLoggingClientAuthorizer( final ClientAuthorizer auth,
                                           final Level acceptLevel,
                                           final Level refuseLevel ) {
        return new ClientAuthorizer() {
            public synchronized void authorize( HttpServer.Request request,
                                                Map securityMap )
                    throws SampException {
                boolean isAuth;
                try {
                    auth.authorize( request, securityMap );
                    log( true, securityMap, null );
                }
                catch ( SampException e ) {
                    log( false, securityMap, e );
                    throw e;
                }
            }
            private void log( boolean accept, Map securityMap,
                              SampException err ) {
                String clTxt = securityMap.containsKey( Metadata.NAME_KEY )
                             ? securityMap.get( Metadata.NAME_KEY ).toString()
                             : securityMap.toString();
                if ( accept ) {
                    logger_.log( acceptLevel,
                                 "Accepted registration for client " + clTxt );
                }
                else {
                    logger_.log( refuseLevel,
                                 "Rejected registration for client " + clTxt
                               + "(" + err.getMessage() + ")" );
                }
            }
        };
    }

    /**
     * Returns the mandatory application name entry from the security map
     * supplied explicitly by clients wishing to register.
     * The relevant key is "samp.name" (Metadata.NAME_KEY).
     * If it's not present and correct, a SampException is thrown
     * indicating that registration is rejected.
     *
     * @param  securityMap  map supplied by client
     * @return   value of samp.name key, not null
     * @throws  SampException  if name not present
     */
    public static String getAppName( Map securityMap ) throws SampException {
        String nameKey = Metadata.NAME_KEY;
        assert "samp.name".equals( nameKey );
        Object appNameObj = securityMap.get( nameKey );
        final String appName;
        if ( appNameObj instanceof String ) {
            return (String) appNameObj;
        }
        else if ( appNameObj == null ) {
            throw new SampException( "No " + nameKey
                                   + " entry in securityInfo map" );
        }
        else {
            throw new SampException( "Wrong data type (not string) for "
                                   + nameKey + " entry in securityInfo map" );
        }
    }
}
