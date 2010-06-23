package org.astrogrid.samp.httpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for use with HttpServer.
 * 
 * <p>This class performs two functions.  Firstly it provides a static
 * {@link #getInstance} method which allows its use in a singleton-like way.
 * The constructor is public, so singleton use is not enforced, but if
 * you need a server but don't need exclusive control over it, obtaining
 * one in this way will ensure that you don't start a new server 
 * (which requires a new socket and other resources) if a suitable one 
 * is already available.
 *
 * <p>Secondly, it provides some utility methods,
 * {@link #exportResource} and {@link #exportFile},
 * useful for turning files or classpath resources into
 * publicly viewable URLs, which is sometimes useful within a SAMP
 * context (for instance when providing an Icon URL in metadata).
 *
 * @author   Mark Taylor
 * @since    22 Jul 2009
 */
public class UtilServer {

    private final HttpServer server_;
    private final Set baseSet_;
    private MultiURLMapperHandler mapperHandler_;
    private ResourceHandler resourceHandler_;

    /**
     * System Property key giving a preferred port number for the server.
     * If unset, or 0, or the chosen port is occupied, a system-chosen
     * value will be used.
     * The property name is {@value}.
     */
    public static final String PORT_PROP = "jsamp.server.port";

    /** Buffer size for copy data from input to output stream. */
    private static int BUFSIZ = 16 * 1024;

    /** Default instance of this class. */
    private static UtilServer instance_;

    private static final Pattern SLASH_REGEX =
        Pattern.compile( "(/*)(.*?)(/*)" );
    private static final Pattern NUMBER_REGEX =
        Pattern.compile( "(.*?)([0-9]+)" );
    private static final Logger logger_ =
        Logger.getLogger( UtilServer.class.getName() );

    /**
     * Constructor.
     * Note, it may be more appropriate to use the {@link #getInstance} method.
     *
     * @param   server  HTTP server providing base services
     */
    public UtilServer( HttpServer server ) throws IOException {
        server_ = server;
        baseSet_ = new HashSet();
    }

    /**
     * Returns the HttpServer associated with this object.
     *
     * @return   a running server instance
     */
    public HttpServer getServer() {
        return server_;
    }

    /**
     * Returns a handler for mapping local to external URLs associated with
     * this server.
     *
     * @return   url mapping handler
     */
    public synchronized MultiURLMapperHandler getMapperHandler() {
        if ( mapperHandler_ == null ) {
            try {
                mapperHandler_ =
                    new MultiURLMapperHandler( server_,
                                               getBasePath( "/export" ) );
            }
            catch ( MalformedURLException e ) {
                throw (AssertionError) new AssertionError().initCause( e );
            }
            server_.addHandler( mapperHandler_ );
        }
        return mapperHandler_;
    }

    /**
     * Returns a handler for general purpose resource serving associated with
     * this server.
     *
     * @return   resource serving handler
     */
    public synchronized ResourceHandler getResourceHandler() {
        if ( resourceHandler_ == null ) {
            resourceHandler_ =
                new ResourceHandler( server_, getBasePath( "/docs" ) );
            server_.addHandler( resourceHandler_ );
        }
        return resourceHandler_;
    }

    /**
     * Exposes a resource from the JVM's classpath as a publicly visible URL.
     * The classloader of this class is used.
     *
     * @param  resource   fully qualified path to a resource in the current
     *                    classpath; separators are "/" characters
     * @return  URL for external reference to the resource
     */
    public URL exportResource( String resource ) throws IOException {
        URL localUrl = getClass().getResource( resource );
        if ( localUrl != null ) {
            return getMapperHandler().addLocalUrl( localUrl );
        }
        else {
            throw new IOException( "Not found on classpath: " + resource );
        }
    }

    /**
     * Exposes a file in the local filesystem as a publicly visible URL.
     *
     * @param  file  a file on a filesystem visible from the local host
     * @return   URL for external reference to the resource
     */
    public URL exportFile( File file ) throws IOException {
        if ( file.exists() ) {
            return getMapperHandler().addLocalUrl( file.toURL() );
        }
        else {
            throw new FileNotFoundException( "No such file: " + file );
        }
    }

    /**
     * May be used to return a unique base path for use with this class's
     * HttpServer.  If all users of this server use this method
     * to get base paths for use with different handlers, nameclash
     * avoidance is guaranteed.
     *
     * @param  txt   basic text for base path
     * @return   base path; will likely bear some resemblance to 
     *           <code>txt</code>, but may be adjusted to ensure uniqueness
     */
    public synchronized String getBasePath( String txt ) {
        Matcher slashMatcher = SLASH_REGEX.matcher( txt );
        String pre;
        String body;
        String post;
        if ( slashMatcher.matches() ) {
            pre = slashMatcher.group( 1 );
            body = slashMatcher.group( 2 );
            post = slashMatcher.group( 3 );
        }
        else {
            assert false;
            pre = "";
            body = txt;
            post = "";
        }
        if ( baseSet_.contains( body ) ) {
            String stem = body;
            int i = 1;
            while ( baseSet_.contains( stem + "-" + i ) ) {
                i++;
            }
            body = stem + "-" + i;
        }
        baseSet_.add( body );
        return pre + body + post;
    }

    /**
     * Returns the default instance of this class.
     * The first time this method is called a new daemon UtilServer 
     * is (lazily) created, and started.  Any subsequent calls will 
     * return the same object, unless {@link #getInstance} is called.
     *
     * @return   default instance of this class
     */
    public static synchronized UtilServer getInstance() throws IOException {
        if ( instance_ == null ) {
            ServerSocket sock = null;
            String sPort = System.getProperty( PORT_PROP );
            if ( sPort != null && sPort.length() > 0 ) {
                int port = Integer.parseInt( sPort );
                try {
                    sock = new ServerSocket( port );
                }
                catch ( BindException e ) {
                    logger_.warning( "Can't open socket on port " + port + "(" 
                                   + e + ") - use another one" );
                }
            }
            if ( sock == null ) {
                sock = new ServerSocket( 0 );
            }
            HttpServer server = new HttpServer( sock );
            server.setDaemon( true );
            server.start();
            instance_ = new UtilServer( server );
        }
        return instance_;
    }

    /**
     * Sets the default instance of this class.
     *
     * @param  server  default instance to be returned by {@link #getInstance}
     */
    public static synchronized void setInstance( UtilServer server ) {
        instance_ = server;
    }

    /**
     * Copies the content of an input stream to an output stream.
     * The input stream is always closed on exit; the output stream is not.
     *
     * @param  in  input stream
     * @param  out  output stream
     */
    static void copy( InputStream in, OutputStream out ) throws IOException {
        byte[] buf = new byte[ BUFSIZ ];
        try {
            for ( int nb; ( nb = in.read( buf ) ) >= 0; ) {
                out.write( buf, 0, nb );
            }
            out.flush();
        }
        finally {
            in.close();
        }
    }
}
