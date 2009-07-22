package org.astrogrid.samp.httpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

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

    /** Buffer size for copy data from input to output stream. */
    private static int BUFSIZ = 16 * 1024;

    /** Default instance of this class. */
    private static UtilServer instance_;

    /**
     * Constructor.  The server uses a daemon process and is started 
     * automatically.
     * Note, it may be more appropriate to use the {@link #getInstance} method.
     */
    public UtilServer() throws IOException {
        baseSet_ = new HashSet();
        server_ = new HttpServer();
        server_.setDaemon( true );
        server_.start();
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
        while ( baseSet_.contains( txt ) ) {
            txt += "X";
            baseSet_.add( txt );
        }
        return txt;
    }

    /**
     * Returns the default instance of this class.
     * The first time this method is called a new UtilServer is (lazily)
     * created.  Any subsequent calls will return the same object.
     *
     * @return   default instance of this class
     */
    public static synchronized UtilServer getInstance() throws IOException {
        if ( instance_ == null ) {
            instance_ = new UtilServer();
        }
        return instance_;
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
