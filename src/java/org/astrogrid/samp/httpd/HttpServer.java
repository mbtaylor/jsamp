package org.astrogrid.samp.httpd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.astrogrid.samp.SampUtils;

/**
 * Simple modular HTTP server.
 * One thread is started per request.  Connections are not kept open between
 * requests.  
 * Suitable for very large response bodies, but not for very large 
 * request bodies.
 * Add one or more {@link HttpServer.Handler}s to serve actual requests.
 * The protocol version served is HTTP/1.0.
 *
 * <p>This class is completely self-contained, so that it can easily be 
 * lifted out and used in other packages if required.
 *
 * @author   Mark Taylor
 * @since    21 Aug 2008
 */
public class HttpServer {

    private final ServerSocket serverSocket_;
    private boolean isDaemon_;
    private List handlerList_;
    private final URL baseUrl_;
    private volatile boolean started_;
    private volatile boolean stopped_;

    /** Header string for MIME content type. */
    public static final String HDR_CONTENT_TYPE = "Content-Type";
    private static final String HDR_CONTENT_LENGTH = "Content-Length";

    /** Status code for OK (200). */
    public static final int STATUS_OK = 200;

    private static final String URI_REGEX =
        "([^\\s\\?]*)\\??([^\\s\\?]*)";
    private static final String HTTP_VERSION_REGEX =
        "HTTP/[0-9]+\\.[0-9]+";
    private static final String HTTP_TOKEN_REGEX =
        "[a-zA-Z0-9_\\.\\-]+";

    private static final Pattern SIMPLE_REQUEST_PATTERN =
        Pattern.compile( "GET" + " " + "(" + "\\S+" + ")" );
    private static final Pattern REQUEST_LINE_PATTERN =
        Pattern.compile( "(" + HTTP_TOKEN_REGEX + ")" // typically GET, HEAD etc
                       + " " + "(" + "\\S+" + ")"
                       + " " + HTTP_VERSION_REGEX );
    private static final Pattern HEADER_PATTERN =
        Pattern.compile( "(" + "[^\\s:]+" +")" + ":\\s*(.*)" );

    private static final Logger logger_ =
        Logger.getLogger( HttpServer.class.getName() );

    /**
     * Constructs a server based on a given socket.
     *
     * @param  socket  listening socket
     */
    public HttpServer( ServerSocket socket ) {
        serverSocket_ = socket;
        isDaemon_ = true;
        handlerList_ = Collections.synchronizedList( new ArrayList() );
        StringBuffer ubuf = new StringBuffer()
            .append( "http://" )
            .append( SampUtils.getLocalhost() );
        int port = socket.getLocalPort();
        if ( port != 80 ) {
            ubuf.append( ':' )
                .append( port );
        }
        try {
            baseUrl_ = new URL( ubuf.toString() );
        }
        catch ( MalformedURLException e ) {
            throw new AssertionError( "Bad scheme http:??" );
        }
    }

    /**
     * Constructs a server based on a default socket, on any free port.
     */
    public HttpServer() throws IOException {
        this( new ServerSocket( 0 ) );
    }

    /**
     * Adds a handler which can serve some requests going through this server.
     *
     * @param  handler   handler to add
     */
    public void addHandler( Handler handler ) {
        handlerList_.add( handler );
    }

    /**
     * Removes a handler previously added by {@link #addHandler}.
     *
     * @param  handler   handler to remove
     */
    public void removeHandler( Handler handler ) {
        handlerList_.remove( handler );
    }

    /**
     * Returns the socket on which this server listens.
     *
     * @return   server socket
     */
    public ServerSocket getSocket() {
        return serverSocket_;
    }

    /**
     * Returns the base URL for this server.
     *
     * @return   base URL
     */
    public URL getBaseUrl() {
        return baseUrl_;
    }

    /**
     * Does the work for providing output corresponding to a given HTTP request.
     * This implementation calls each Handler in turn and the first one
     * to provide a non-null response is used.
     *
     * @param  request  represents an HTTP request that has been received
     * @return   represents the content of an HTTP response that should be sent
     */
    public Response serve( Request request ) {
        Handler[] handlers =
            (Handler[]) handlerList_.toArray( new Handler[ 0 ] );
        for ( int ih = 0; ih < handlers.length; ih++ ) {
            Handler handler = handlers[ ih ];
            Response response = handler.serveRequest( request );
            if ( response != null ) {
                return response;
            }
        }
        return createErrorResponse( 404, "No handler for URL" );
    }

    /**
     * Determines whether the server thread will be a daemon thread or not.
     * Must be called before {@link #start} to have an effect.
     * The default is true.
     *
     * @param   isDaemon  whether server thread will be daemon
     * @see     java.lang.Thread#setDaemon
     */
    public void setDaemon( boolean isDaemon ) {
        isDaemon_ = isDaemon;
    }

    /**
     * Starts the server if it is not already started.
     */
    public synchronized void start() {
        if ( ! started_ ) {
            Thread server = new Thread( "HTTP Server" ) {
                public void run() {
                    try {
                        while ( ! stopped_ ) {
                            try {
                                final Socket sock = serverSocket_.accept();
                                new Thread( "HTTP Request" ) {
                                    public void run() {
                                        try {
                                            serveRequest( sock );
                                        }
                                        catch ( Throwable e ) {
                                            logger_.log( Level.WARNING,
                                                         "Httpd error", e );
                                        }
                                    }
                                }.start();
                            }
                            catch ( IOException e ) {
                                if ( ! stopped_ ) {
                                    logger_.log( Level.WARNING,
                                                 "Socket error", e );
                                }
                            }
                        }
                    }
                    finally {
                        HttpServer.this.stop();
                    }
                }
            };
            server.setDaemon( isDaemon_ );
            logger_.info( "Server " + getBaseUrl() + " starting" );
            server.start();
            started_ = true;
            logger_.config( "Server " + getBaseUrl() + " started" );
        }
    }

    /**
     * Stops the server if it is currently running.  Processing of any requests
     * which have already been received is completed.
     */
    public synchronized void stop() {
        if ( ! stopped_ ) {
            stopped_ = true;
            logger_.info( "Server " + getBaseUrl() + " stopping" );
            try {
                serverSocket_.close();
            }
            catch ( IOException e ) {
                logger_.log( Level.WARNING,
                             "Error during server stop: " + e, e );
            }
        }
    }

    /**
     * Indicates whether this server is currently running.
     *
     * @return   true if running
     */
    public boolean isRunning() {
        return started_ && ! stopped_;
    }

    /**
     * Called by the server thread for each new connection.
     *
     * @param  sock   client connection socket
     */
    protected void serveRequest( Socket sock ) throws IOException {

        // Try to generate a request object by examining the socket's 
        // input stream.  If that fails, generate a response representing
        // the error.
        InputStream in = sock.getInputStream();
        in = new BufferedInputStream( in );
        Response response = null;
        Request request = null; 
        try {
            request = parseRequest( in, sock.getRemoteSocketAddress() );

            // If there was no input, make no response at all.
            if ( request == null ) {
                return;
            }
        }
        catch ( HttpException e ) {
            response = e.createResponse();
        }
        catch ( IOException e ) {
            response = createErrorResponse( 400, "I/O error", e );
        }
        catch ( Throwable e ) {
            response = createErrorResponse( 500, "Server error", e );
        }

        // If we have a request (and hence no error response) process it to
        // obtain a response object.
        if ( response == null ) {
            assert request != null;
            try {
                response = serve( request );
            }
            catch ( Throwable e ) {
                response = createErrorResponse( 500, e.toString(), e );
            }
        }
        Level level = response.getStatusCode() == 200 ? Level.CONFIG
                                                      : Level.WARNING;
        if ( logger_.isLoggable( level ) ) {
            StringBuffer sbuf = new StringBuffer();
            if ( request != null ) {
                sbuf.append( request.getMethod() )
                    .append( ' ' )
                    .append( request.getUrl() );
            }
            else {
                sbuf.append( "<bad-request>" );
            }
            sbuf.append( " --> " )
                .append( response.statusCode_ )
                .append( ' ' )
                .append( response.statusPhrase_ );
            logger_.log( level, sbuf.toString() );
        }

        // Send the response back to the client.
        BufferedOutputStream bos =
            new BufferedOutputStream( sock.getOutputStream() );
        try {
            response.writeResponse( bos );
            bos.flush();
        }
        finally {
            try {
                bos.close();
            }
            catch ( IOException e ) {
            }
        }
    }

    /**
     * Takes the input stream from a client connection and turns it into
     * a Request object.
     * As a special case, if the input stream has no content at all,
     * null is returned.
     *
     * @param   in   input stream
     * @param   remoteAddress  address of requesting client
     * @return  parsed request, or null
     */
    private static Request parseRequest( InputStream in,
                                         SocketAddress remoteAddress )
            throws IOException {

        // Read the pre-body part.
        String[] hdrLines = readHeaderLines( in );

        // No text at all?
        if ( hdrLines == null ) {
            return null;
        }

        // No header content?
        if ( hdrLines.length == 0 ) {
            throw new HttpException( 400, "Empty request" );
        }

        // HTTP/0.9 style simple request - probably rare.
        Matcher simpleMatcher = SIMPLE_REQUEST_PATTERN.matcher( hdrLines[ 0 ] );
        if ( simpleMatcher.matches() ) {
            return new Request( "GET", simpleMatcher.group( 1 ),
                                new HashMap(), remoteAddress, null );
        }

        // Normal HTTP/1.0 request.
        // First parse the request line.
        Matcher fullMatcher = REQUEST_LINE_PATTERN.matcher( hdrLines[ 0 ] );
        if ( fullMatcher.matches() ) {
            String method = fullMatcher.group( 1 );
            String uri = fullMatcher.group( 2 );

            // Then read and parse header lines.
            HttpHeaderMap headerMap = new HttpHeaderMap();
            int iLine = 1;
            boolean headerEnd = false;
            int contentLength = 0;
            for ( ; iLine < hdrLines.length; iLine++ ) {
                String line = hdrLines[ iLine ];
                Matcher headerMatcher = HEADER_PATTERN.matcher( line );
                if ( headerMatcher.matches() ) {
                    String key = headerMatcher.group( 1 );
                    String value = headerMatcher.group( 2 );

                    // Cope with continuation lines.
                    boolean cont = true;
                    while ( iLine + 1 < hdrLines.length && cont ) {
                        cont = false;
                        String line1 = hdrLines[ iLine + 1 ];
                        if ( line1.length() > 0 ) {
                            char c1 = line1.charAt( 0 ); 
                            if ( c1 == ' ' || c1 == '\t' ) {
                                value += line1.trim();
                                iLine++;
                                cont = true;
                            }
                        }
                    }

                    // Store the header.
                    headerMap.addHeader( key, value );

                    // Iff we have a content-length line it means we can expect
                    // a body later.
                    if ( key.equalsIgnoreCase( HDR_CONTENT_LENGTH ) ) {
                        try {
                            contentLength =
                                Integer.parseInt( value.trim() );
                        }
                        catch ( NumberFormatException e ) {
                            throw new HttpException( 400, 
                                                     "Failed to parse "
                                                    + key + " header "
                                                    + value );
                        }
                    }
                }
            }

            // Read body if there is one.
            final byte[] body;
            if ( contentLength > 0 ) {
                body = new byte[ contentLength ];
                int ib = 0;
                while ( ib < contentLength ) {
                    int nb = in.read( body, ib, contentLength - ib );
                    if ( nb < 0 ) {
                        throw new HttpException( 500,
                            "Insufficient bytes for declared Content-Length: "
                           + ib + "<" + contentLength );
                    }
                    ib += nb;
                }
                assert ib == contentLength;
            }
            else {
                body = null;
            }
 
            // Decode escaped characters in the requested URI.
            uri = SampUtils.uriDecode( uri );

            // Return the request.
            return new Request( method, uri, headerMap, remoteAddress, body );
        }

        // Unrecognised.
        else {
            throw new HttpException( 400, "Bad request" );
        }
    }

    /**
     * Reads the header lines from an HTTP client connection input stream.
     * All lines are read up to the first CRLF, which is when the body starts.
     * The input stream is left in a state ready to read the first body line.
     * As a special case, if the input stream has no content at all,
     * null is returned.
     *
     * @param  is   socket input stream
     * @return   array  of header lines (including initial request line);
     *           the terminal blank line is not included, or null
     */
    private static String[] readHeaderLines( InputStream is )
            throws IOException {
        List lineList = new ArrayList();
        StringBuffer sbuf = new StringBuffer();
        boolean hasChars = false;
        for ( int c; ( c = is.read() ) >= 0; ) {
            hasChars = true;
            switch ( c ) {

                // CRLF is the correct HTTP line terminator.
                case '\r':
                    if ( is.read() == '\n' ) {
                        if ( sbuf.length() == 0 ) {
                            return (String[])
                                   lineList.toArray( new String[ 0 ] );
                        }
                        else {
                            lineList.add( sbuf.toString() );
                            sbuf.setLength( 0 );
                        }
                    }
                    else {
                        throw new HttpException( 400, "CR w/o LF" );
                    }
                    break;

                // HTTP 1.1 recommends that a lone LF is also tolerated as a
                // line terminator.
                case '\n':
                    if ( sbuf.length() == 0 ) {
                        return (String[])
                               lineList.toArray( new String[ 0 ] );
                    }
                    else {
                        lineList.add( sbuf.toString() );
                        sbuf.setLength( 0 );
                    }
                    break;

                default:
                   sbuf.append( (char) c );
            }
        }

        // No input.
        if ( ! hasChars ) {
            return null;
        }

        // Special case: can handle HTTP/0.9 type simple requests. 
        // Probably very rare.
        if ( lineList.size() == 1 ) {
            String line = (String) lineList.get( 0 );
            if ( SIMPLE_REQUEST_PATTERN.matcher( line ).matches() ) {
                return new String[] { line };
            }
        }

        // If it's got this far, there was no blank line.
        throw new HttpException( 500, "No CRLF line" );
    }

    /**
     * Returns a header value from a header map.
     * Key value is case-insensitive.
     * In the (undesirable) case that multiple keys with the same
     * case-insensitive value exist, the values are concatenated with
     * comma separators, as per RFC2616 section 4.2.
     *
     * @param  headerMap  map
     * @param  key   header key
     * @return   value of map entry with case-insensitive match for key
     */
    public static String getHeader( Map headerMap, String key ) {
        List valueList = new ArrayList();
        for ( Iterator it = headerMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            if ( ((String) entry.getKey()).equalsIgnoreCase( key ) ) {
                Object value = entry.getValue();
                if ( value instanceof String ) {
                    valueList.add( value );
                }
            }
        }
        int nval = valueList.size();
        if ( nval == 0 ) {
            return null;
        }
        else if ( nval == 1 ) {
            return (String) valueList.get( 0 );
        }
        else {
            StringBuffer sbuf = new StringBuffer();
            for ( Iterator vit = valueList.iterator(); vit.hasNext(); ) {
                sbuf.append( (String) vit.next() );
                if ( vit.hasNext() ) {
                    sbuf.append( ", " );
                }
            }
            return sbuf.toString();
        }
    }

    /**
     * Utility method to create an error response.
     *
     * @param   code  status code
     * @param   phrase  status phrase
     * @return   new response object
     */
    public static Response createErrorResponse( int code, String phrase ) {
        return new Response( code, phrase, new HashMap() ) {
            public void writeBody( OutputStream out ) {
            }
        };
    }

    /**
     * Creates an HTTP response indicating that the requested method
     * (GET, POST, etc) is not supported.
     *
     * @param  supportedMethods  list of the methods which are supported
     * @return   error response
     */
    public static Response create405Response( String[] supportedMethods ) {
        Map hdrMap = new LinkedHashMap();
        StringBuffer mlist = new StringBuffer();
        for ( int i = 0; i < supportedMethods.length; i++ ) {
            if ( i > 0 ) {
                mlist.append( ", " );
            }
            mlist.append( supportedMethods[ i ] );
        }
        hdrMap.put( "Allow", mlist.toString() );
        hdrMap.put( "Content-Length", "0" );
        return new Response( 405, "Method not allowed", hdrMap ) {
            public void writeBody( OutputStream out ) {
            }
        };
    }

    /**
     * Utility method to create an error response given an exception.
     *
     * @param   code  status code
     * @param   phrase  status phrase
     * @param   e   exception which caused the trouble
     * @return   new response object
     */
    public static Response createErrorResponse( int code, String phrase,
                                                final Throwable e ) {
        Map hdrMap = new LinkedHashMap();
        hdrMap.put( HDR_CONTENT_TYPE, "text/plain" );
        return new Response( code, phrase, hdrMap ) {
            public void writeBody( OutputStream out ) {
                PrintStream pout = new PrintStream( out );
                e.printStackTrace( pout );
                pout.flush();
            }
        };
    }

    /**
     * Represents a parsed HTTP client request.
     */
    public static class Request {
        private final String method_;
        private final String url_;
        private final Map headerMap_;
        private final SocketAddress remoteAddress_;
        private final byte[] body_;

        /**
         * Constructor.
         *
         * @param  method  HTTP method string (GET, HEAD etc)
         * @param  url     requested URL path (should start "/")
         * @param  headerMap  map of HTTP request header key-value pairs
         * @param  remoteAddress  address of the client making the request
         * @param  body  bytes comprising request body, or null if none present
         */
        public Request( String method, String url, Map headerMap,
                        SocketAddress remoteAddress, byte[] body ) {
            method_ = method;
            url_ = url;
            headerMap_ = headerMap;
            remoteAddress_ = remoteAddress;
            body_ = body;
        }

        /**
         * Returns the request method string.
         *
         * @return   GET, HEAD, or whatever
         */
        public String getMethod() {
            return method_;
        }

        /**
         * Returns the request URL string.  This should be a path starting
         * "/" (the hostname part is not present).
         *
         * @return  url path
         */
        public String getUrl() {
            return url_;
        }

        /**
         * Returns a map of key-value pairs representing HTTP request headers.
         * Note that for HTTP usage, header keys are case-insensitive
         * (RFC2616 sec 4.2); the {@link #getHeader} utility method 
         * can be used to interrogate the returned map.
         *
         * @return   header map
         */
        public Map getHeaderMap() {
            return headerMap_;
        }

        /**
         * Returns the address of the client which made this request.
         *
         * @return   requesting client's socket address
         */
        public SocketAddress getRemoteAddress() {
            return remoteAddress_;
        }

        /**
         * Returns the body of the HTTP request if there was one.
         *
         * @return  body bytes or null
         */
        public byte[] getBody() {
            return body_;
        }

        public String toString() {
            StringBuffer sbuf = new StringBuffer()
                .append( method_ )
                .append( ' ' )
                .append( url_ );
            if ( headerMap_ != null && ! headerMap_.isEmpty() ) {
                sbuf.append( "\n    " )
                    .append( headerMap_ );
            }
            if ( body_ != null && body_.length > 0 ) {
                sbuf.append( "\n    " )
                    .append( "body[" )
                    .append( body_.length )
                    .append( ']' );
            }
            return sbuf.toString();
        }
    }

    /**
     * Represents a response to an HTTP request.
     */
    public static abstract class Response {
        private final int statusCode_;
        private final String statusPhrase_;
        private final Map headerMap_;

        /**
         * Constructor.
         *
         * @param  statusCode  3-digit status code
         * @param  statusPhrase  text string passed to client along 
         *                       with the status code
         * @param  headerMap    map of key-value pairs representing response
         *                      header information; should normally contain
         *                      at least a content-type key
         */
        public Response( int statusCode, String statusPhrase, Map headerMap ) {
            if ( Integer.toString( statusCode ).length() != 3 ) {
                throw new IllegalArgumentException( "Bad status "
                                                  + statusCode );
            }
            statusCode_ = statusCode;
            statusPhrase_ = statusPhrase;
            headerMap_ = headerMap;
        }

        /**
         * Returns the 3-digit status code.
         *
         * @return  status code
         */
        public int getStatusCode() {
            return statusCode_;
        }

        /**
         * Returns the status phrase.
         *
         * @return  status phrase
         */
        public String getStatusPhrase() {
            return statusPhrase_;
        }

        /**
         * Returns a map of key-value pairs representing HTTP response headers.
         * Note that for HTTP usage, header keys are case-insensitive
         * (RFC2616 sec 4.2); the {@link #getHeader} utility method 
         * can be used to interrogate the returned map.
         *
         * @return   header map
         */
        public Map getHeaderMap() {
            return headerMap_;
        }
   
        /**
         * Implemented to generate the bytes in the body of the response.
         *
         * @param  out  destination stream for body bytes
         */
        public abstract void writeBody( OutputStream out ) throws IOException;

        /**
         * Writes this response to an output stream in a way suitable for
         * replying to the client.
         * Status line and any headers are written, then {@link #writeBody}
         * is called.
         *
         * @param  out  destination stream
         */
        public void writeResponse( OutputStream out ) throws IOException {
            String statusLine = new StringBuffer()
                .append( "HTTP/1.0" )
                .append( ' ' )
                .append( getStatusCode() )
                .append( ' ' )
                .append( getStatusPhrase() )
                .append( '\r' )
                .append( '\n' )
                .toString();
            out.write( statusLine.getBytes( "UTF-8" ) );
            if ( headerMap_ != null ) {
                StringBuffer sbuf = new StringBuffer();
                for ( Iterator it = getHeaderMap().entrySet().iterator();
                      it.hasNext(); ) {
                    Map.Entry entry = (Map.Entry) it.next();
                    sbuf.setLength( 0 );
                    String line = sbuf
                        .append( entry.getKey() )
                        .append( ':' )
                        .append( ' ' )
                        .append( entry.getValue() )
                        .append( '\r' )
                        .append( '\n' )
                        .toString();
                    out.write( line.getBytes( "UTF-8" ) );
                }
            }
            out.write( '\r' );
            out.write( '\n' );
            writeBody( out );
        }
    }

    /**
     * Convenience class for representing an error whose content should be
     * returned to the user as an HTTP erro response of some kind.
     */
    private static class HttpException extends IOException {
        private final int code_;
        private final String phrase_;

        /**
         * Constructor.
         *
         * @param  code  3-digit status code
         * @param  phrase  status phrase
         */
        HttpException( int code, String phrase ) {
            code_ = code;
            phrase_ = phrase;
        }

        /**
         * Turns this exception into a response object.
         *
         * @return  error response
         */
        Response createResponse() {
            return createErrorResponse( code_, phrase_ );
        }
    }

    /**
     * Map implementation suitable for storing HTTP headers.
     * It should be populated using the {@link #addHeader}, not {@link #put},
     * method.
     * This implementation should be used when a header is being constructed
     * from an uncontrolled source of (key,value) pairs.
     * If you are adding headers yourself and know that you won't duplicate
     * keys, then a normal Map implementation will do.
     */
    static class HttpHeaderMap extends LinkedHashMap {

        /**
         * Adds a header value to this map.
         * This differs from put in two subtle ways.
         * First, key matching is case-insensitive.
         * Second, if a value for the given key already exists, the new
         * value will be appended after a comma, rather than replacing
         * the old entry.  See RFC2616 section 4.2 for the HTTP rules.
         *
         * @param   key  header name
         * @param   value  header value
         */
        public void addHeader( String key, String value ) {
            boolean added = false;
            for ( Iterator it = entrySet().iterator();
                  it.hasNext() && ! added; ) {
                Map.Entry entry = (Map.Entry) it.next();
                if ( ((String) entry.getKey()).equalsIgnoreCase( key ) ) {
                    entry.setValue( entry.getValue() + ", " + value );
                    added = true;
                }
            }
            if ( ! added ) {
                put( key, value );
            }
        }
    }

    /**
     * Implemented to serve data for some URLs.
     */
    public interface Handler {

        /**
         * Provides a response to an HTTP request.
         * A handler which does not recognise the URL should simply return null;
         * in this case there may be another handler which is able to serve
         * the request.  If the URL appears to be in this handler's domain but
         * the request cannot be served for some reason, an error response
         * should be returned.
         *
         * @param   request  HTTP request
         * @return   response  response to request, or null
         */
        Response serveRequest( Request request );
    }
}
