package org.astrogrid.samp.web;

import java.util.logging.Level;
import java.util.logging.Logger;
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
    public static final ClientAuthorizer SWING =
        createLoggingClientAuthorizer( new SwingClientAuthorizer( null, false ),
                                       Level.INFO, Level.INFO );

    private static final Logger logger_ =
        Logger.getLogger( ClientAuthorizers.class.getName() );

    private ClientAuthorizers() {
    }

    /**
     * Returns one of the known authorizer instances given its name.
     *
     * @param  name  one of "false", "true", "swing"
     * @return   authorizer instance
     * @throws  IllegalArgumentException  if <code>name</code> is unknown
     */
    public static ClientAuthorizer getClientAuthorizer( String name ) {
        if ( "false".equalsIgnoreCase( name ) ) {
            return FALSE;
        }
        else if ( "true".equalsIgnoreCase( name ) ) {
            return TRUE;
        }
        else if ( "swing".equalsIgnoreCase( name ) ) {
            return SWING;
        }
        else {
            throw new IllegalArgumentException( "unknown auth " + name );
        }
    }

    /**
     * Returns a new authorizer instance which always produces the same
     * authorization status.
     *
     * @author  policy  value to return from the <code>authorize</code> method
     * @return   new authorizer
     */
    public static ClientAuthorizer
                  createFixedClientAuthorizer( final boolean policy ) {
        return new ClientAuthorizer() {
            public boolean authorize( HttpServer.Request request,
                                      String appName ) {
                return policy;
            }
        };
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
            public synchronized boolean authorize( HttpServer.Request request,
                                                   String appName ) {
                boolean accept = auth.authorize( request, appName );
                log( accept, "\"" + appName + "\"" );
                return accept;
            }
            private void log( boolean accept, String appName ) {
                if ( accept ) {
                    logger_.log( acceptLevel,
                                 "Accepted registration for client "
                               + appName );
                }
                else {
                    logger_.log( refuseLevel,
                                 "Rejected registration for client "
                               + appName );
                }
            }
        };
    }
}
