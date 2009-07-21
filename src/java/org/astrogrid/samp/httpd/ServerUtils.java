package org.astrogrid.samp.httpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for use with HttpServer.
 * This class maintains a static (singleton) instance of 
 * {@link HttpServer} and some associated handlers.
 * Client code which wants to use an HttpServer but does not need
 * exclusive access to one can use the methods in this class;
 * this can reduce the number of servers running (hence ports open
 * and other resources used).
 * The {@link #exportResource} and {@link #exportFile} methods are
 * useful conveniences for turning files or classpath resources into
 * publicly viewable URLs, which is sometimes useful within a SAMP
 * context (for instance when providing an Icon URL in metadata).
 *
 * @author   Mark Taylor
 * @since    21 Jul 2009
 */
public class ServerUtils {

    private static HttpServer server_;
    private static MultiURLMapperHandler mapperHandler_;
    private static final Set baseSet_ = new HashSet();

    /** Buffer size for copy data from input to output stream. */
    public static int BUFSIZ = 16 * 1024;

    /**
     * Private constructor prevents instantiation.
     */
    private ServerUtils() {
    }

    /**
     * Returns the default server instance.
     * It is created and started lazily.
     *
     * @return   a running server instance
     */
    public static synchronized HttpServer getServer() throws IOException {
        if ( server_ == null ) {
            HttpServer server = new HttpServer();
            server.setDaemon( true );
            server.start();
            server_ = server;
        }
        return server_;
    }

    /**
     * Returns a handler for mapping local to external URLs associated
     * with the default server instance.
     *
     * @return   url mapping handler
     */
    public static synchronized MultiURLMapperHandler getMapperHandler()
            throws IOException {
        if ( mapperHandler_ == null ) {
            HttpServer server = getServer();
            MultiURLMapperHandler mapperHandler =
                new MultiURLMapperHandler( server, getBasePath( "/export" ) );
            server.addHandler( mapperHandler );
            mapperHandler_ = mapperHandler;
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
    public static URL exportResource( String resource ) throws IOException {
        URL localUrl =
            ServerUtils.class.getResource( resource );
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
    public static URL exportFile( File file ) throws IOException {
        if ( file.exists() ) {
            return getMapperHandler().addLocalUrl( file.toURL() );
        }
        else {
            throw new FileNotFoundException( "No such file: " + file );
        }
    }

    /**
     * May be used to return a unique base path for use with this class's
     * default server.  If all users of this server use this method 
     * to get base paths for use with different handlers, nameclash
     * avoidance is guaranteed.
     *
     * @param  txt   basic text for base path
     * @return   base path; will bear some resemblance to <code>txt</code>,
     *           but may be adjusted to ensure uniqueness
     */
    public static synchronized String getBasePath( String txt ) {
        while( baseSet_.contains( txt ) ) {
            txt += "X";
            baseSet_.add( txt );
        }
        return txt;
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
