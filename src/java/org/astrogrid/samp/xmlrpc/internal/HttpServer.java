package org.astrogrid.samp.xmlrpc.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Simple modular HTTP server.
 * One thread is started per request.  Connections are not kept open between
 * requests.  
 * Suitable for very large response bodies, but not for very large 
 * request bodies.
 * Concrete subclasses need to implement the {@link #serve} method.
 * The protocol version served is HTTP/1.0.
 *
 * @author   Mark Taylor
 * @since    21 Aug 2008
 */
class HttpServer {

    private final ServerSocket serverSocket_;
    private Thread serverThread_;
    private boolean isDaemon_;
    private List handlerList_;

    /** Header string for MIME content type. */
    public static final String HDR_CONTENT_TYPE = "Content-Type";
    private static final String HDR_CONTENT_LENGTH = "Content-Length";

    /** Status code for OK (200). */
    public static final int STATUS_OK = 200;

    private static final String URI_REGEX =
        "([^\\s\\?]*)\\??([^\\s\\?]*)";
    private static final String HTTP_VERSION_REGEX =
        "HTTP/[0-9]+\\.[0-9]+";

    private static final Pattern SIMPLE_REQUEST_PATTERN =
        Pattern.compile( "GET" + " " + "(" + "\\S+" + ")" );
    private static final Pattern REQUEST_LINE_PATTERN =
        Pattern.compile( "(GET|HEAD|POST)" + " " + "(" + "\\S+" + ")"
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
        handlerList_ = new ArrayList();
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
     * Does the work for providing output corresponding to a given HTTP request.
     * This implementation calls each Handler in turn and the first one
     * to provide a non-null response is used.
     *
     * @param  request  represents an HTTP request that has been received
     * @return   represents the content of an HTTP response that should be sent
     */
    protected Response serve( Request request ) {
        for ( Iterator it = handlerList_.iterator(); it.hasNext(); ) {
            Handler handler = (Handler) it.next();
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
        if ( serverThread_ == null ) {
            Thread server = new Thread( "HTTP Server" ) {
                public void run() {
                    try {
                        while ( ! isInterrupted() ) {
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
                                logger_.log( Level.WARNING, "Socket error", e );
                            }
                        }
                    }
                    finally {
                        serverThread_ = null;
                    }
                }
            };
            server.setDaemon( isDaemon_ );
            server.start();
        }
    }

    /**
     * Stops the server if it is currently running.  Processing of any requests
     * which have already been received is completed.
     */
    public synchronized void stop() {
        Thread serverThread = serverThread_;
        if ( serverThread != null ) {
            serverThread.interrupt();
            serverThread_ = null;
        }
    }

    /**
     * Called by the server thread for each new connection.
     *
     * @param  sock   client connection socket
     */
    private void serveRequest( Socket sock ) throws IOException {

        // Try to generate a request object by examining the socket's 
        // input stream.  If that fails, generate a response representing
        // the error.
        BufferedInputStream bis =
            new BufferedInputStream( sock.getInputStream() );
        Response response = null;
        Request request = null; 
        try {
            request = parseRequest( bis );
        }
        catch ( HttpException e ) {
            response = e.createResponse();
        }
        catch ( IOException e ) {
            response = createErrorResponse( 400, "I/O error", e );
        }
        catch ( Throwable e ) {
            response = createErrorResponse( 500, "Server error", e );
            logger_.log( Level.WARNING, "Server error", e );
        }

        // If we have a request (and hence no error response) process it to
        // obtain a response object.
        if ( response == null ) {
            assert request != null;
            try {
                response = serve( request );
            }
            catch ( Throwable e ) {
                response = createErrorResponse( 500, e.getMessage(), e );
            }
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
     *
     * @param   in   input stream
     */
    private static Request parseRequest( InputStream in )
            throws IOException {

        // Read the pre-body part.
        String[] hdrLines = readHeaderLines( in );

        // No content?
        if ( hdrLines.length == 0 ) {
            throw new HttpException( 400, "Empty request" );
        }

        // HTTP/0.9 style simple request - probably rare.
        Matcher simpleMatcher = SIMPLE_REQUEST_PATTERN.matcher( hdrLines[ 0 ] );
        if ( simpleMatcher.matches() ) {
            return new Request( "GET", simpleMatcher.group( 1 ),
                                new HashMap(), null );
        }

        // Normal HTTP/1.0 request.
        // First parse the request line.
        Matcher fullMatcher = REQUEST_LINE_PATTERN.matcher( hdrLines[ 0 ] );
        if ( fullMatcher.matches() ) {
            String method = fullMatcher.group( 1 );
            String uri = fullMatcher.group( 2 );

            // Then read and parse header lines.
            Map headerMap = new HashMap();
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
                    headerMap.put( key, value );

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
                in.read( body );
            }
            else {
                body = null;
            }
 
            // Decode escaped characters in the requeted URI.
            uri = URLDecoder.decode( uri, "UTF-8" );

            // Return the request.
            return new Request( method, uri, headerMap, body );
        }

        // Unrecognised.
        else {
            throw new HttpException( 500, "Bad request" );
        }
    }

    /**
     * Reads the header lines from an HTTP client connection input stream.
     * All lines are read up to the first CRLF, which is when the body starts.
     * The input stream is left in a state ready to read the first body line.
     *
     * @param  is   socket input stream
     * @return   array  of header lines (including initial request line);
     *           the terminal blank line is not included
     */
    private static String[] readHeaderLines( InputStream is )
            throws IOException {
        List lineList = new ArrayList();
        StringBuffer sbuf = new StringBuffer();
        for ( int c; ( c = is.read() ) >= 0; ) {
            switch ( c ) {
                case '\n':
                   throw new HttpException( 400, "LF w/o CR" );
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
                default:
                   sbuf.append( (char) c );
            }
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
     * Utility method to create an error response.
     *
     * @param   code  status code
     * @param   phrase  status phrase
     * @return   new response object
     */
    public static Response createErrorResponse( int code, String phrase ) {
        return new Response( code, phrase, null ) {
            protected void writeBody( OutputStream out ) {
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
        Map hdrMap = new HashMap();
        hdrMap.put( HDR_CONTENT_TYPE, "text/plain" );
        return new Response( code, phrase, hdrMap ) {
            protected void writeBody( OutputStream out ) {
                PrintStream pout = new PrintStream( out );
                e.printStackTrace( pout );
                pout.flush();
            }
        };
    }

    /**
     * Represents a parsed HTTP client request.
     */
    static class Request {
        private final String method_;
        private final String url_;
        private final Map headerMap_;
        private final byte[] body_;

        /**
         * Constructor.
         *
         * @param  method  HTTP method string (GET, HEAD etc)
         * @param  url     requested URL path (should start "/")
         * @param  headerMap  map of HTTP request header key-value pairs
         * @param  body  bytes comprising request body, or null if none present
         */
        Request( String method, String url, Map headerMap, byte[] body ) {
            method_ = method;
            url_ = url;
            headerMap_ = headerMap;
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
         *
         * @return   headers
         */
        public Map getHeaderMap() {
            return headerMap_;
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
    static abstract class Response {
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
        Response( int statusCode, String statusPhrase, Map headerMap ) {
            if ( Integer.toString( statusCode ).length() != 3 ) {
                throw new IllegalArgumentException( "Bad status "
                                                  + statusCode );
            }
            statusCode_ = statusCode;
            statusPhrase_ = statusPhrase;
            headerMap_ = headerMap;
        }

        /**
         * Implemented to generate the bytes in the body of the response.
         *
         * @param  out  destination stream for body bytes
         */
        protected abstract void writeBody( OutputStream out )
                throws IOException;

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
                .append( statusCode_ )
                .append( ' ' )
                .append( statusPhrase_ )
                .append( '\r' )
                .append( '\n' )
                .toString();
            out.write( statusLine.getBytes( "UTF-8" ) );
            if ( headerMap_ != null ) {
                StringBuffer sbuf = new StringBuffer();
                for ( Iterator it = headerMap_.entrySet().iterator();
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
     * Implemented to serve data for some URLs.
     */
    interface Handler {

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
