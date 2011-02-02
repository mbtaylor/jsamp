package org.astrogrid.samp.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * HttpServer which allows or rejects cross-origin access according to
 * the W3C Cross-Origin Resource Sharing standard.
 * This standard is used by XMLHttpResource Level 2 and some other
 * web-based platforms, implemented by a number of modern browsers,
 * and works by the browser inserting and interpreting special headers
 * when cross-origin requests are made by sandboxed clients.
 * The effect is that sandboxed clients will under some circumstances
 * be permitted to access resources served by instances of this server,
 * where they wouldn't for an HTTP server which did not take special
 * measures.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 * @see  <a href="http://www.w3.org/TR/cors/"
 *          >Cross-Origin Resource Sharing W3C Standard</a>
 */
public class CorsHttpServer extends HttpServer {

    private final OriginAuthorizer authorizer_;

    private static final String ORIGIN_KEY = "Origin";
    private static final String ALLOW_ORIGIN_KEY =
        "Access-Control-Allow-Origin";
    private static final String REQUEST_METHOD_KEY =
        "Access-Control-Request-Method";
    private static final String ALLOW_METHOD_KEY =
        "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS_KEY =
        "Access-Control-Allow-Headers";
    private static final Pattern ORIGIN_REGEX =
        Pattern.compile( "http://[a-zA-Z0-9_-]+"
                       + "(\\.[a-zA-Z0-9_-]+)*(:[0-9]+)?" );
    private static final Logger logger_ =
        Logger.getLogger( CorsHttpServer.class.getName() );

    /**
     * Constructor.
     *
     * @param  socket  socket hosting the service
     * @param  authorizer   defines which domains requests will be
     *                      permitted from
     */
    public CorsHttpServer( ServerSocket socket, OriginAuthorizer authorizer )
            throws IOException {
        super( socket );
        authorizer_ = authorizer;
    }

    public Response serve( Request request ) {
        Map hdrMap = request.getHeaderMap();
        String method = request.getMethod();
        String originTxt = getHeader( hdrMap, ORIGIN_KEY );
        if ( originTxt != null ) {
            String reqMethod = getHeader( hdrMap, REQUEST_METHOD_KEY );
            if ( method.equals( "OPTIONS" ) && reqMethod != null ) {
                return servePreflightOriginRequest( request, originTxt,
                                                    reqMethod );
            }
            else {
                return serveSimpleOriginRequest( request, originTxt );
            }
        }
        else {
            return super.serve( request );
        }
    }

    /**
     * Does the work for serving <em>simple</em> requests which bear an
     * origin header.  Simple requests are effectively ones which do not
     * require pre-flight requests - see the CORS standard for details.
     *
     * @param   request   HTTP request
     * @param   originTxt  content of the Origin header
     * @return  HTTP response
     */
    private Response serveSimpleOriginRequest( Request request,
                                               String originTxt ) {
        Response response = super.serve( request );
        if ( isAuthorized( originTxt ) ) {
            Map headerMap = response.getHeaderMap();
            if ( getHeader( headerMap, ALLOW_ORIGIN_KEY ) == null ) {
                headerMap.put( ALLOW_ORIGIN_KEY, originTxt );
            }
        }
        return response;
    }

    /**
     * Does the work for serving pre-flight requests.
     * See the CORS standard for details.
     *
     * @param   request  HTTP request
     * @param   originTxt   content of the Origin header
     * @param   reqMethod  content of the Access-Control-Request-Method header
     * @return  HTTP response
     */
    private Response servePreflightOriginRequest( Request request,
                                                  String originTxt,
                                                  String reqMethod ) {
        Map hdrMap = new LinkedHashMap();
        hdrMap.put( "Content-Length", "0" );
        if ( isAuthorized( originTxt ) ) {
            hdrMap.put( ALLOW_ORIGIN_KEY, originTxt );
            hdrMap.put( ALLOW_METHOD_KEY, reqMethod );
            hdrMap.put( ALLOW_HEADERS_KEY, "Content-Type" ); // allow all here?
        }
        return new Response( 200, "OK", hdrMap ) {
            public void writeBody( OutputStream out ) {
            }
        };
    }

    /**
     * Determines whether a given origin is permitted access.
     * This is done by interrogating this server's OriginAuthorizer policy.
     * Results are cached.
     *
     * @param  originTxt  content of Origin header
     */
    private boolean isAuthorized( String originTxt ) {

        // CORS sec 5.1 says multiple space-separated origins may be present
        // - but why??  Treat the string as a single origin for now.
        // Not incorrect, though possibly annoying if the same origin
        // crops up multiple times in different sets (unlikely as far
        // as I can see).
        boolean hasLegalOrigin;
        try {
            checkOriginList( originTxt );
            hasLegalOrigin = true;
        }
        catch ( RuntimeException e ) {
            logger_.warning( "Origin header: " + e.getMessage() );
            hasLegalOrigin = false;
        }
        return hasLegalOrigin && authorizer_.authorize( originTxt );
    }

    /**
     * Checks that the content of an Origin header is syntactically legal.
     *
     * @param   originTxt  content of Origin header
     * @throws  IllegalArgumentExeption if originTxt does not represent
     *          a legal origin or (non-empty) list of origins
     */
    private static void checkOriginList( String originTxt ) {
        String[] origins = originTxt.split( " +" );
        if ( origins.length > 0 ) {
            for ( int i = 0; i < origins.length; i++ ) {
                if ( ! ORIGIN_REGEX.matcher( origins[ i ] ).matches() ) {
                    throw new IllegalArgumentException(
                        "Bad origin syntax: \"" + origins[ i ] + "\"" );
                }
            }
        }
        else {
            throw new IllegalArgumentException( "No origins supplied" );
        }
    }
}
