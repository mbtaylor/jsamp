package org.astrogrid.samp.web;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Platform;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * ClientProfile implementation for Web Profile.
 *
 * @author   Mark Taylor
 * @since    3 Feb 2011
 */
public class WebClientProfile implements ClientProfile {

    private final SampXmlRpcClientFactory xClientFactory_;
    private final Map securityMap_;
    private final URL hubEndpoint_;

    /** Web Profile hub port number ({@value}). */
    public static final int WEBSAMP_PORT = 21012;

    /**
     * Path on WEBSAMP_PORT web server at which XML-RPC server lives
     * ({@value}).
     */
    public static final String WEBSAMP_PATH = "/";

    /**
     * Prefix to hub interface operation names for XML-RPC method names
     * ({@value}).
     */
    public static final String WEBSAMP_HUB_PREFIX = "samp.webhub.";

    /**
     * Prefix to client interface opeation names for XML-RPC method names
     * ({@value}).
     */
    public static final String WEBSAMP_CLIENT_PREFIX = "";

    /**
     * RegInfo map key for URL translation service base URL
     * ({@value}).
     */
    public static final String URLTRANS_KEY = "samp.url-translator";

    /**
     * Prefix in SAMP_HUB value indicating web profile application name
     * ({@value}).
     */
    public static final String WEBPROFILE_HUB_PREFIX = "web-appname:";

    /**
     * Constructor with configuration options.
     *
     * @param  securityMap  map containing security information for registration
     * @param  xClientFactory  XML-RPC client factory
     * @param  hubEndpoint  XML-RPC endpoint for hub server
     */
    public WebClientProfile( Map securityMap,
                             SampXmlRpcClientFactory xClientFactory,
                             URL hubEndpoint ) {
        securityMap_ = securityMap;
        xClientFactory_ = xClientFactory;
        hubEndpoint_ = hubEndpoint;
    }

    /**
     * Constructor with declared client name.
     *
     * @param  appName  client's declared application name
     *                  (samp.name entry in security-info map)
     */
    public WebClientProfile( String appName ) {
        this( createSecurityMap( appName ),
              XmlRpcKit.getInstance().getClientFactory(),
              getDefaultHubEndpoint() );
    }

    /**
     * Constructor with no arguments.  The client's declared application
     * name will be as given by {@link #getDefaultAppName}.
     */
    public WebClientProfile() {
        this( getDefaultAppName() );
    }

    public boolean isHubRunning() {
        try {
            Socket sock = new Socket( hubEndpoint_.getHost(),
                                      hubEndpoint_.getPort() );
            sock.close();
            return true;
        }
        catch ( IOException e ) {
            return false;
        }
    }

    public HubConnection register() throws SampException {
        try {
            return new WebHubConnection( xClientFactory_
                                        .createClient( hubEndpoint_ ),
                                         securityMap_ );
        }
        catch ( SampException e ) {
            for ( Throwable ex = e; ex != null; ex = ex.getCause() ) {
                if ( ex instanceof ConnectException ) {
                    return null;
                }
            }
            throw e;
        }
        catch ( ConnectException e ) {
            return null;
        }
        catch ( IOException e ) {
            throw new SampException( e );
        }
    }

    /**
     * Returns the hub XML-RPC endpoint used by this profile.
     *
     * @return  hub endpoint URL
     */
    public URL getHubEndpoint() {
        return hubEndpoint_;
    }

    /**
     * Returns the hub XML-RPC endpoint defined by the Web Profile.
     *
     * @return   Web Profile hub endpoint URL
     */
    public static URL getDefaultHubEndpoint() {
        String surl = "http://" + SampUtils.getLocalhost() + ":"
                    + WEBSAMP_PORT + WEBSAMP_PATH;
        try {
            return new URL( surl );
        }
        catch ( MalformedURLException e ) {
            throw new AssertionError( "http scheme not supported?? " + surl );
        }
    }

    /**
     * Returns a default instance of this profile.
     *
     * @return  default web client profile instance
     */
    public static WebClientProfile getInstance() {
        return new WebClientProfile();
    }

    /**
     * Returns the default application name used by this profile if none
     * is supplied explicitly.
     * If the SAMP_HUB environment variable has the form
     * "web-appname:&lt;appname&gt;" it is taken from there;
     * otherwise it's something like "Unknown".
     *
     * @return  default declared client name
     */
    public static String getDefaultAppName() {
        String hubloc = Platform.getPlatform()
                       .getEnv( DefaultClientProfile.HUBLOC_ENV );
        return hubloc != null && hubloc.startsWith( WEBPROFILE_HUB_PREFIX )
             ? hubloc.substring( WEBPROFILE_HUB_PREFIX.length() )
             : "Unknown Application";
    }

    /**
     * Constructs a security-info map suitable for presentation at
     * registration time, containing the mandatory samp.name entry.
     *
     * @param  appName  samp.name entry
     * @return  security map
     */
    private static Map createSecurityMap( String appName ) {
        Map map = new HashMap();
        map.put( Metadata.NAME_KEY, appName );
        return map;
    }
}
