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
    private static final String authUsage_ =
        "[-web:auth swing|true|false|extreme]";
    private static final String corsUsage_ = "[-web:[no]cors]";
    private static final String flashUsage_ = "[-web:[no]flash]";
    private static final String silverlightUsage_ = "[-web:[no]silverlight]";
    private static final String urlcontrolUsage_ = "[-web:[no]urlcontrol]";
    private static final String restrictMtypeUsage_ =
        "[-web:[no]restrictmtypes]";

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
            corsUsage_,
            flashUsage_,
            silverlightUsage_,
            urlcontrolUsage_,
            restrictMtypeUsage_,
        };
    }

    public HubProfile createHubProfile( List flagList ) throws IOException {

        // Process flags.
        String logType = "none";
        String authType = "swing";
        boolean useCors = true;
        boolean useFlash = true;
        boolean useSilverlight = false;
        boolean urlControl = true;
        boolean restrictMtypes = true;
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
            else if ( arg.equals( "-web:cors" ) ) {
                it.remove();
                useCors = true;
            }
            else if ( arg.equals( "-web:nocors" ) ) {
                it.remove();
                useCors = false;
            }
            else if ( arg.equals( "-web:flash" ) ) {
                it.remove();
                useFlash = true;
            }
            else if ( arg.equals( "-web:noflash" ) ) {
                it.remove();
                useFlash = false;
            }
            else if ( arg.equals( "-web:silverlight" ) ) {
                it.remove();
                useSilverlight = true;
            }
            else if ( arg.equals( "-web:nosilverlight" ) ) {
                it.remove();
                useSilverlight = false;
            }
            else if ( arg.equals( "-web:urlcontrol" ) ) {
                urlControl = true;
            }
            else if ( arg.equals( "-web:nourlcontrol" ) ) {
                urlControl = false;
            }
            else if ( arg.equals( "-web:restrictmtypes" ) ) {
                restrictMtypes = true;
            }
            else if ( arg.equals( "-web:norestrictmtypes" ) ) {
                restrictMtypes = false;
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
        sfact.setOriginAuthorizer( useCors ? OriginAuthorizers.TRUE
                                           : OriginAuthorizers.FALSE );
        sfact.setAllowFlash( useFlash );
        sfact.setAllowSilverlight( useSilverlight );

        // Prepare client authorizer.
        final ClientAuthorizer clientAuth;
        if ( "swing".equalsIgnoreCase( authType ) ) {
            clientAuth = ClientAuthorizers
                        .createLoggingClientAuthorizer(
                             new HubSwingClientAuthorizer( null ),
                             Level.INFO, Level.INFO );
        }
        else if ( "extreme".equalsIgnoreCase( authType ) ) {
            clientAuth = ClientAuthorizers
                        .createLoggingClientAuthorizer(
                             new ExtremeSwingClientAuthorizer( null ),
                             Level.WARNING, Level.INFO );
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

        // Prepare subscriptions mask.
        SubscriptionMask subsMask = restrictMtypes
                                  ? ListSubscriptionMask.DEFAULT
                                  : ListSubscriptionMask.ALL;

        // Construct and return an appropriately configured hub profile.
        return new WebHubProfile( sfact, clientAuth, subsMask,
                                  WebHubProfile.createKeyGenerator(),
                                  urlControl );
    }

    public Class getHubProfileClass() {
        return WebHubProfile.class;
    }
}
