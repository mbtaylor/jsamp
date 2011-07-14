package org.astrogrid.samp.web;

import java.lang.SecurityException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.httpd.URLMapperHandler;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.xmlrpc.ActorHandler;

/**
 * SampXmlRpcHandler implementation which passes Web Profile-type XML-RPC calls
 * to a hub connection factory to provide a Web Profile hub server.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
class WebHubXmlRpcHandler extends ActorHandler {

    private final WebHubActorImpl impl_;
    private static final Logger logger_ =
        Logger.getLogger( WebHubXmlRpcHandler.class.getName() );

    /**
     * Constructor.
     *
     * @param  profile  hub connection factory
     * @param  auth   client authorizer
     * @param  keyGen   key generator for private keys
     * @param  baseUrl  base URL of HTTP server, used for URL translation
     */
    public WebHubXmlRpcHandler( ClientProfile profile, ClientAuthorizer auth,
                                KeyGenerator keyGen, URL baseUrl ) {
        super( WebClientProfile.WEBSAMP_HUB_PREFIX, WebHubActor.class,
               new WebHubActorImpl( profile, auth, keyGen, baseUrl ) );
        impl_ = (WebHubActorImpl) getActor();
    }

    public Object handleCall( String fqName, List params, Object reqObj )
            throws Exception {
        String regMethod = WebClientProfile.WEBSAMP_HUB_PREFIX + "register";
        if ( regMethod.equals( fqName ) &&
             reqObj instanceof HttpServer.Request ) {
            HttpServer.Request req = (HttpServer.Request) reqObj;
            final Map securityMap;
            if ( params.size() == 1 && params.get( 0 ) instanceof Map ) {
                securityMap = (Map) params.get( 0 );
            }
            else if ( params.size() == 1 &&
                      params.get( 0 ) instanceof String ) {
                securityMap = new HashMap();
                securityMap.put( Metadata.NAME_KEY, (String) params.get( 0 ) );
                logger_.info( "Deprecated register call signature "
                            + "(arg is string appName not map security-info)" );
            }
            else {
                throw new IllegalArgumentException( "Bad args for " + regMethod
                                                  + "(map)" );
            }
            Map result = impl_.register( req, securityMap );
            assert result != null;
            return result;
        }
        else {
            return super.handleCall( fqName, params, reqObj );
        }
    }

    /**
     * Returns a handler suitable for performing URL translations on behalf
     * of sandboxed clients as required by the Web Profile.
     *
     * @return   url translation handler
     */
    public HttpServer.Handler getUrlTranslationHandler() {
        return impl_.getUrlTranslationHandler();
    }

    protected Object invokeMethod( Method method, Object obj, Object[] args )
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke( obj, args );
    }

    /**
     * WebHubActor implementation.
     */
    private static class WebHubActorImpl implements WebHubActor {

        private final ClientProfile profile_;
        private final ClientAuthorizer auth_;
        private final KeyGenerator keyGen_;
        private final Map regMap_;
        private final URLTranslationHandler urlTranslator_;
        private final URL baseUrl_;

        /**
         * Constructor.
         *
         * @param  profile   hub connection factory
         * @param  auth  client authorizer
         * @param  keyGen   key generator for private keys
         * @param  baseUrl  HTTP server base URL
         */
        public WebHubActorImpl( ClientProfile profile, ClientAuthorizer auth,
                                KeyGenerator keyGen, URL baseUrl ) {
            profile_ = profile;
            auth_ = auth;
            keyGen_ = keyGen;
            baseUrl_ = baseUrl;
            regMap_ = Collections.synchronizedMap( new HashMap() );
            urlTranslator_ =
                new URLTranslationHandler( "/proxied/", regMap_.keySet() );
        }

        /**
         * Returns a handler suitable for performing URL translations on behalf
         * of sandboxed clients as required by the Web Profile.
         *
         * @return   url translation handler
         */
        public HttpServer.Handler getUrlTranslationHandler() {
            return urlTranslator_;
        }

        /**
         * Attempt client registration.  An exception is thrown if registration
         * fails for any reason.
         *
         * @param  request  HTTP request from applicant
         * @param  securityMap  map of required security information
         *                      supplied by applicant
         * @return  registration information if registration is successful
         */
        public RegInfo register( HttpServer.Request request, Map securityMap )
                throws SecurityException, SampException {
            if ( profile_.isHubRunning() ) {
                if ( ! CorsHttpServer
                      .isLocalHost( request.getRemoteAddress() ) ) {
                    String alert =
                        "Registration attempt from non-local remote host - "
                      + "should have been blocked earlier by HTTP server";
                    logger_.severe( alert );
                    throw new SecurityException( alert );
                }
                Object appNameObj = securityMap.get( Metadata.NAME_KEY );
                final String appName;
                if ( appNameObj instanceof String ) {
                    appName = (String) appNameObj;
                }
                else {
                    throw new SampException( "Wrong data type (not string) for "
                                           + Metadata.NAME_KEY + " securityInfo"
                                           + " entry" );
                }
                boolean isAuth = auth_.authorize( request, appName );
                if ( ! isAuth ) {
                    throw new SecurityException( "Registration denied" );
                }
                else {
                    HubConnection connection = profile_.register();
                    if ( connection != null ) {
                        String clientKey = keyGen_.next();
                        regMap_.put( clientKey,
                                     new Registration( connection ) );
                        String urlTrans = baseUrl_
                                        + urlTranslator_
                                         .getTranslationBasePath( clientKey );
                        RegInfo regInfo =
                            new RegInfo( connection.getRegInfo() );
                        regInfo.put( RegInfo.PRIVATEKEY_KEY, clientKey );
                        regInfo.put( WebClientProfile.URLTRANS_KEY, urlTrans );
                        return regInfo;
                    }
                    else {
                        throw new SampException( "Hub is not running" );
                    }
                }
            }
            else {
                throw new SampException( "Hub not running" );
            }
        }

        public void unregister( String clientKey ) throws SampException {
            HubConnection connection = getConnection( clientKey );
            regMap_.remove( clientKey );
            connection.unregister();
        }

        public void allowReverseCallbacks( String clientKey, String allow )
                throws SampException {
            boolean isAllowed = SampUtils.decodeBoolean( allow );
            Registration reg = getRegistration( clientKey );
            synchronized ( reg ) {
                if ( isAllowed == ( reg.callable_ != null ) ) {
                    return;
                }
                else if ( isAllowed ) {
                    WebCallableClient callable = new WebCallableClient();
                    reg.connection_.setCallable( callable );
                    reg.callable_ = callable;
                }
                else {
                    reg.connection_.setCallable( null );
                    reg.callable_.endCallbacks();
                    reg.callable_ = null;
                }
                assert isAllowed == ( reg.callable_ != null );
            }
        }

        public List pullCallbacks( String clientKey, String timeout )
                throws SampException {
            WebCallableClient callable = getRegistration( clientKey ).callable_;
            if ( callable != null ) {
                return callable
                      .pullCallbacks( SampUtils.decodeInt( timeout ) );
            }
            else {
                throw new SampException( "Client is not callable (first invoke"
                                       + " allowReverseCallbacks)" );
            }
        }

        public void declareMetadata( String clientKey, Map meta )
                throws SampException {
            getConnection( clientKey ).declareMetadata( meta );
        }

        public Map getMetadata( String clientKey, String clientId )
                throws SampException {
            return getConnection( clientKey ).getMetadata( clientId );
        }

        public void declareSubscriptions( String clientKey, Map subs )
                throws SampException {
            getRegistration( clientKey ).subs_ =
                new Subscriptions( subs == null ? new HashMap() : subs );
            getConnection( clientKey ).declareSubscriptions( subs );
        }

        public Map getSubscriptions( String clientKey, String clientId )
                throws SampException {
            return getConnection( clientKey ).getSubscriptions( clientId );
        }

        public List getRegisteredClients( String clientKey )
                throws SampException {
            return Arrays.asList( getConnection( clientKey )
                                 .getRegisteredClients() );
        }

        public Map getSubscribedClients( String clientKey, String mtype )
                throws SampException {
            return getConnection( clientKey ).getSubscribedClients( mtype );
        }
   
        public void notify( String clientKey, String recipientId, Map msg )
                throws SampException {
            getConnection( clientKey ).notify( recipientId, msg );
        }

        public List notifyAll( String clientKey, Map msg )
                throws SampException {
            return getConnection( clientKey ).notifyAll( msg );
        }

        public String call( String clientKey, String recipientId, String msgTag,
                            Map msg )
                throws SampException {
            return getConnection( clientKey ).call( recipientId, msgTag, msg );
        }

        public Map callAll( String clientKey, String msgTag, Map msg )
                throws SampException {
            return getConnection( clientKey ).callAll( msgTag, msg );
        }

        public Map callAndWait( String clientKey, String recipientId, Map msg,
                                String timeout )
                throws SampException {
            return getConnection( clientKey )
                  .callAndWait( recipientId, msg,
                                SampUtils.decodeInt( timeout ) );
        }

        public void reply( String clientKey, String msgId, Map response )
                throws SampException {
            getConnection( clientKey ).reply( msgId, response );
        }

        public void ping() {
            if ( ! profile_.isHubRunning() ) {
                throw new RuntimeException( "No hub running" );
            }
        }

        public void ping( String clientKey ) {
            ping();
        }

        /**
         * Returns the registration object associated with a given private key.
         *
         * @param  privateKey  private key string known by client and hub
         *         to identify the connection
         * @return   registration object for client with key
         *           <code>privateKey</code>
         * @throws  SampException   if no client is known with that private key
         */
        private Registration getRegistration( String privateKey )
                throws SampException {
            Registration reg = (Registration) regMap_.get( privateKey );
            if ( reg == null ) {
                throw new SampException( "Unknown client key" );
            }
            else {
                return reg;
            }
        }

        /**
         * Returns the connection object associated with a given private key.
         *
         * @param  privateKey  private key string known by client and hub
         *         to identify the connection
         * @return   connection object for client with key
         *           <code>privateKey</code>
         * @throws  SampException   if no client is known with that private key
         */
        private HubConnection getConnection( String privateKey )
                throws SampException {
            return getRegistration( privateKey ).connection_;
        }
    }

    /**
     * HTTP handler which provides URL translation services for sandboxed
     * clients.
     */
    private static class URLTranslationHandler implements HttpServer.Handler {
        private final String basePath_;
        private final Set keySet_;

        /**
         * Constructor.
         *
         * @param  basePath   base path for HTTP server
         * @param  keySet   set of strings which contains keys for all
         *                  currently registered clients
         */
        public URLTranslationHandler( String basePath, Set keySet ) {
            if ( ! basePath.startsWith( "/" ) ) {
                basePath = "/" + basePath;
            }
            if ( ! basePath.endsWith( "/" ) ) {
                basePath = basePath + "/";
            }
            basePath_ = basePath;
            keySet_ = keySet;
        }

        /**
         * Returns the translation base path that can be used by a client
         * with a given private key.
         *
         * @param  privateKey  client private key
         * @return   URL translation base path that can be used by a
         *           registered client with the given private key
         */
        public String getTranslationBasePath( String privateKey ) {
            return basePath_ + privateKey + "?";
        }

        public HttpServer.Response serveRequest( HttpServer.Request request ) {

            // Ignore requests outside this handler's domain.
            String path = request.getUrl();
            if ( ! path.startsWith( basePath_ ) ) {
                return null;
            }

            // Ensure the URL has a query part.
            String relPath = path.substring( basePath_.length() );
            int qIndex = relPath.indexOf( '?' );
            if ( qIndex < 0 ) {
                return HttpServer.createErrorResponse( 404, "Not Found" );
            }

            // Ensure a valid key for authorization is present; this makes
            // sure that only registered clients can use this service.
            String authKey = relPath.substring( 0, qIndex );
            if ( ! keySet_.contains( authKey ) ) {
                return HttpServer.createErrorResponse( 403, "Forbidden" );
            }

            // Extract the URL whose translation is being requested.
            assert path.substring( 0, path.indexOf( '?' ) + 1 )
                       .equals( getTranslationBasePath( authKey ) );
            String targetString;
            try {
                targetString =
                    SampUtils.uriDecode( relPath.substring( qIndex + 1 ) );
            }
            catch ( RuntimeException e ) {
                return HttpServer.createErrorResponse( 400, "Bad Request" );
            }
            URL targetUrl;
            try {
                targetUrl = new URL( targetString );
            }
            catch ( MalformedURLException e ) {
                return HttpServer.createErrorResponse( 400, "Bad Request" );
            }

            // Perform the translation and return the result.
            return URLMapperHandler.mapUrlResponse( request.getMethod(),
                                                    targetUrl );
        }
    }

    /**
     * Utility class to aggregate information about a registered client.
     */
    private static class Registration {
        final HubConnection connection_;
        WebCallableClient callable_;
        Subscriptions subs_;

        /**
         * Constructor.
         *
         * @param   connection   hub connection
         */
        Registration( HubConnection connection ) {
            connection_ = connection;
        }
    }
}
