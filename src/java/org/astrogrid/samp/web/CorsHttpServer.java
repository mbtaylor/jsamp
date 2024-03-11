package org.astrogrid.samp.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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
    private static final String REQUEST_PRIVATE_NETWORK_KEY =
        "Access-Control-Request-Private-Network";
    private static final String ALLOW_PRIVATE_NETWORK_KEY =
        "Access-Control-Allow-Private-Network";

    // This regex is constructed with reference to RFC6454 and RFC3986.
    // It is less rigorous than those, since the host production in RFC3986
    // is quite complex, but the required checking is not all that critical.
    private static final Pattern ORIGIN_REGEX =
        Pattern.compile( "[A-Za-z][A-Za-z0-9+.-]*://.+" );

    private static final InetAddress localHostAddress_ = getLocalHostAddress();
    private static final Logger logger_ =
        Logger.getLogger( CorsHttpServer.class.getName() );

    /**
     * System property ({@value}) which can be used to supply host addresses
     * explicitly permitted to connect via the Web Profile alongside
     * the local host.
     * Normally any non-local host is blocked from access to the CORS
     * web server for security reasons.  However, any host specified
     * by hostname or IP number as one element of a comma-separated
     * list in the value of this system property will also be allowed.
     * This might be used to allow access from a "friendly" near-local
     * host like a tablet.
     */
    public static final String EXTRAHOSTS_PROP = "jsamp.web.extrahosts";

    /** Set of permitted InetAddrs along side localhost. */
    private static final Set extraAddrSet_ =
        new HashSet( Arrays.asList( getExtraHostAddresses() ) );

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
        if ( ! isPermittedHost( request.getRemoteAddress() ) ) {
            return createNonLocalErrorResponse( request );
        }
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

        /* Manipulate headers according to the "Private Network Access"
         * proposal - see https://wicg.github.io/private-network-access/.
         * At time of writing (2024) it's not clear how widespread browser
         * implementation of this security feature will be, but it's likely
         * this behaviour will be either beneficial or harmless in the
         * context of future browser development. */
        if ( "true".equals( getHeader( request.getHeaderMap(),
                                       REQUEST_PRIVATE_NETWORK_KEY ) ) ) {
            hdrMap.put( ALLOW_PRIVATE_NETWORK_KEY, "true" );
        }

        /* Return results. */
        return new Response( 200, "OK", hdrMap ) {
            public void writeBody( OutputStream out ) {
            }
        };
    }

    /**
     * Returns an HTTP error response complaining about attempted access
     * from a disallowed host.
     *
     * @param  request  offending request
     * @return   HTTP 403 response
     */
    public static Response createNonLocalErrorResponse( Request request ) {
        int status = 403;
        String msg = "Forbidden";
        String method = request.getMethod();
        if ( "HEAD".equals( method ) ) {
            return createErrorResponse( status, msg );
        }
        else {
            Map hdrMap = new LinkedHashMap();
            hdrMap.put( HDR_CONTENT_TYPE, "text/plain" );
            byte[] mbuf;
            try {
                mbuf = ( "Access to server from non-local hosts "
                       + "is not permitted.\r\n" )
                      .getBytes( "UTF-8" );
            }
            catch ( UnsupportedEncodingException e ) {
                logger_.warning( "Unsupported UTF-8??" );
                mbuf = new byte[ 0 ];
            }
            final byte[] mbuf1 = mbuf;
            hdrMap.put( "Content-Length", Integer.toString( mbuf1.length ) );
            return new Response( status, msg, hdrMap ) {
                public void writeBody( OutputStream out ) throws IOException {
                    out.write( mbuf1 );
                    out.flush();
                }
            };
        }
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
     * Indicates whether a network address is known to represent
     * a host permitted to access this server.
     * That generally means the local host, but "extra" hosts may be
     * permitted as well.
     *
     * @param   address  socket address
     * @return  true  iff address is known to be permitted
     */
    public boolean isPermittedHost( SocketAddress address ) {
        return isLocalHost( address ) || isExtraHost( address );
    }

    /**
     * Indicates whether the given socket address is from the local host.
     *
     * @param   address  socket to test
     * @return  true if the socket is known to be local
     */
    public static boolean isLocalHost( SocketAddress address ) {
        if ( address instanceof InetSocketAddress ) {
            InetAddress iAddress = ((InetSocketAddress) address).getAddress();
            return iAddress != null
                && ( iAddress.isLoopbackAddress() ||
                     iAddress.equals( localHostAddress_ ) );
        }
        else {
            return false;
        }
    }

    /**
     * Returns the inet address of the local host, or null if not available.
     *
     * @return  local host address or null
     */
    private static InetAddress getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost();
        }
        catch ( UnknownHostException e ) {
            logger_.log( Level.WARNING,
                         "Can't determine local host address", e );
            return null;
        }
    }

    /**
     * Acquires and returns a list of permitted non-local hosts from the
     * environment.
     *
     * @return  list of addresses for non-local hosts permitted to access
     *          CORS web servers in this JVM
     */
    private static InetAddress[] getExtraHostAddresses() {
        String list;
        try {
            list = System.getProperty( EXTRAHOSTS_PROP );
        }
        catch ( SecurityException e ) {
            list = null;
        }
        String[] names;
        if ( list != null ) {
            list = list.trim();
            names = list.length() > 0 ? list.split( ", *" ) : new String[ 0 ];
        }
        else {
            names = new String[ 0 ];
        }
        int naddr = names.length;
        List addrList = new ArrayList();
        for ( int i = 0; i < naddr; i++ ) {
            String name = names[ i ];
            try {
                addrList.add( InetAddress.getByName( name ) );
                logger_.warning( "Adding web hub exception for host "
                               + "\"" + name + "\"" );
            }
            catch ( UnknownHostException e ) {
                logger_.warning( "Unknown host \"" + name + "\""
                               + " - not adding web hub exception" );
            }
        }
        return (InetAddress[]) addrList.toArray( new InetAddress[ 0 ] );
    }

    /**
     * Indicates whether a given address represents one of the "extra" hosts
     * permitted to access this server alongside the localhost.
     *
     * @param  addr   address of non-local host to test
     * @return  true iff host is permitted to access this server
     */
    public static boolean isExtraHost( SocketAddress addr ) {
        return addr instanceof InetSocketAddress
            && extraAddrSet_.contains( ((InetSocketAddress) addr)
                                      .getAddress() );
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
