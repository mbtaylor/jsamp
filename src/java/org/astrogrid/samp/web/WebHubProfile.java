package org.astrogrid.samp.web;

import java.io.IOException;
import java.net.ServerSocket;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;
import org.astrogrid.samp.xmlrpc.internal.RpcLoggingInternalServer;
import org.astrogrid.samp.xmlrpc.internal.XmlLoggingInternalServer;

/**
 * HubProfile implementation for Web Profile.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class WebHubProfile implements HubProfile {

    private final InternalServer xServer_;
    private final ClientAuthorizer auth_;
    private final KeyGenerator keyGen_;
    private boolean started_;
    private boolean shutdown_;
    private WebHubXmlRpcHandler wxHandler_;

    /**
     * Constructs a profile with configuration options.
     *
     * @param   xServer  server providing HTTP and XML-RPC implementation;
     *          normally supplied from one of this class's
     *          <code>createSampXmlRpcServer</code> methods
     * @param   auth  client authorizer implementation
     * @param   keyGen  key generator for private keys
     */
    public WebHubProfile( InternalServer xServer, ClientAuthorizer auth,
                          KeyGenerator keyGen ) {
        xServer_ = xServer;
        auth_ = auth;
        keyGen_ = keyGen;
    }

    /**
     * Constructs a profile with default configuration.
     */
    public WebHubProfile() throws IOException {
        this( createSampXmlRpcServer( null ),
              new HubSwingClientAuthorizer( null ),
              createKeyGenerator() );
    }

    public void start( ClientProfile profile ) {
        synchronized ( this ) {
            if ( started_ ) {
                throw new IllegalStateException( "Already started" );
            }
            started_ = true;
        }
        HttpServer hServer = xServer_.getHttpServer();
        wxHandler_ = new WebHubXmlRpcHandler( profile, auth_, keyGen_,
                                              hServer.getBaseUrl() );
        xServer_.addHandler( wxHandler_ );
        hServer.addHandler( wxHandler_.getUrlTranslationHandler() );
        hServer.start();
    }

    public void shutdown() {
        synchronized ( this ) {
            if ( ! started_ ) {
                throw new IllegalStateException( "Not started" );
            }
            if ( shutdown_ ) {
                return;
            }
            shutdown_ = true;
        }
        xServer_.removeHandler( wxHandler_ );
        wxHandler_ = null;
        xServer_.getHttpServer().stop();
    }

    /**
     * Returns an InternalServer suitable for use with a WebHubProfile
     * with choice of logging options.
     *
     * @param  logType  logging type;
     *                  may be "http", "rpc", "xml", "none" or null
     * @return   new server for use with WebHubProfile
     */
    public static InternalServer createSampXmlRpcServer( String logType )
            throws IOException {
        return createSampXmlRpcServer( logType,
                                       new ServerSocket( WebClientProfile
                                                        .WEBSAMP_PORT ),
                                       WebClientProfile.WEBSAMP_PATH,
                                       OriginAuthorizers.TRUE,
                                       true, true );
    }

    /**
     * Returns an InternalServer suitable for use with a WebHubProfile
     * with various options.
     *
     * @param  logType  logging type;
     *                  may be "http", "rpc", "xml", "none" or null
     * @param  socket  socket on which HTTP server will run
     * @param  xmlrpcPath  path on socket for XML-RPC endpoint
     *                     (should start with "/")
     * @param   oAuth  origin authorizer to control access at the origin level
     * @param   allowFlash  true iff Adobe's cross-origin policy should be
     *                      honoured
     * @param   allowSilverlight  true iff Microsoft's Silverlight cross-origin
     *                     policy should be honoured
     * @return   new server for use with WebHubProfile
     */
    public static InternalServer
                  createSampXmlRpcServer( String logType,
                                          ServerSocket socket,
                                          String xmlrpcPath,
                                          OriginAuthorizer oAuth,
                                          boolean allowFlash,
                                          boolean allowSilverlight )
            throws IOException {
        String path = WebClientProfile.WEBSAMP_PATH;
        CorsHttpServer hServer = "http".equals( logType )
                               ? new LoggingCorsHttpServer( socket, oAuth,
                                                            System.err )
                               : new CorsHttpServer( socket, oAuth );
        if ( allowFlash ) {
            hServer.addHandler( OpenPolicyResourceHandler
                               .createFlashPolicyHandler( oAuth ) );
        }
        if ( allowSilverlight ) {
            hServer.addHandler( OpenPolicyResourceHandler
                               .createSilverlightPolicyHandler( oAuth ) );
        }
        hServer.setDaemon( true );
        if ( "rpc".equals( logType ) ) {
            return new RpcLoggingInternalServer( hServer, path, System.err );
        }
        else if ( "xml".equals( logType ) ) {
            return new XmlLoggingInternalServer( hServer, path, System.err );
        }
        else if ( "none".equals( logType ) || "http".equals( logType ) ||
                  logType == null || logType.length() == 0 ) {
            return new InternalServer( hServer, xmlrpcPath );
        }
        else {
            throw new IllegalArgumentException( "Unknown logType " + logType );
        }
    }

    /**
     * Convenience method to return a new key generator
     * suitable for use with a WebHubProfile.
     *
     * @return  new key generator for web hub private keys
     */
    public static KeyGenerator createKeyGenerator() {
        return new KeyGenerator( "wk:", 24, KeyGenerator.createRandom() );
    }
}
