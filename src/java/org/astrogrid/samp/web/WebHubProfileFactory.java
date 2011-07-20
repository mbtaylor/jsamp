package org.astrogrid.samp.web;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.HubProfileFactory;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

/**
 * HubProfileFactory implementation for Web Profile.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class WebHubProfileFactory implements HubProfileFactory {

    private static final String logUsage_ = "[-web:log none|http|xml|rpc]";
    private static final String authUsage_ = "[-web:auth swing|true|false]";

    /**
     * Returns "web".
     */
    public String getName() {
        return "web";
    }

    public String[] getFlagsUsage() {
        return new String[] {
            logUsage_,
            authUsage_,
        };
    }

    public HubProfile createHubProfile( List flagList ) throws IOException {

        // Process flags.
        String logType = "none";
        String authType = "swing";
        for ( Iterator it = flagList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-web:log" ) ) {
                it.remove();
                if ( it.hasNext() ) {
                    logType = (String) it.next();
                    it.remove();
                }
                else {
                    throw new IllegalArgumentException( "Usage: " + logUsage_ );
                }
            }
            else if ( arg.equals( "-web:auth" ) ) {
                it.remove();
                if ( it.hasNext() ) {
                    authType = (String) it.next();
                    it.remove();
                }
                else {
                    throw new IllegalArgumentException( "Usage: "
                                                      + authUsage_ );
                }
            }
        }

        // Prepare HTTP server.
        WebHubProfile.ServerFactory sfact = new WebHubProfile.ServerFactory();
        try {
            sfact.setLogType( logType );
        }
        catch ( IllegalArgumentException e ) {
            throw (IllegalArgumentException)
                  new IllegalArgumentException( "Unknown log type " + logType
                                              + "; Usage: " + logUsage_ )
                 .initCause( e );
        }

        // Prepare client authorizer.
        final ClientAuthorizer clientAuth;
        if ( "swing".equalsIgnoreCase( authType ) ) {
            clientAuth = ClientAuthorizers
                        .createLoggingClientAuthorizer(
                             new HubSwingClientAuthorizer( null ),
                             Level.INFO, Level.INFO );
        }
        else if ( "true".equalsIgnoreCase( authType ) ) {
            clientAuth = ClientAuthorizers.TRUE;
        }
        else if ( "false".equalsIgnoreCase( authType ) ) {
            clientAuth = ClientAuthorizers.FALSE;
        }
        else {
            throw new IllegalArgumentException( "Unknown authorizer type "
                                              + authType + "; Usage: "
                                              + authUsage_ );
        }

        // Construct and return an appropriately configured hub profile.
        return new WebHubProfile( sfact, clientAuth,
                                  WebHubProfile.createKeyGenerator() );
    }

    public Class getHubProfileClass() {
        return WebHubProfile.class;
    }
}
