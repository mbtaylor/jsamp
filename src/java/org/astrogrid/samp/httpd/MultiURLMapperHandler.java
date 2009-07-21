package org.astrogrid.samp.httpd;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler implementation which allows the server to serve multiple 
 * separate resources which are available to it, but not necessarily to
 * external clients, as URLs.  The main use for this is if the URLs
 * are jar:-type ones (not available to clients outside the current JVM)
 * or file:-type ones (not available to clients on different hosts).
 * Only single resources, not whole trees, can be exported in this way.
 *
 * <p>The functionality of this class overlaps with that of
 * {@link URLMapperHandler}.  They may be merged at some point.
 *
 * @author   Mark Taylor
 * @since    21 Jul 2009
 */
public class MultiURLMapperHandler implements HttpServer.Handler {

    private final HttpServer server_;
    private final String basePath_;
    private final URL baseUrl_;
    private final Map urlMap_;
    private int resourceCount_;

    /**
     * Constructor.
     *
     * @param  server  server within which this handler will be used
     * @param  basePath  path of served resources relative to the base
     *         URL of the server itself
     */
    public MultiURLMapperHandler( HttpServer server, String basePath )
            throws MalformedURLException {
        server_ = server;
        if ( ! basePath.startsWith( "/" ) ) {
            basePath = "/" + basePath;
        }
        if ( ! basePath.endsWith( "/" ) ) {
            basePath = basePath + "/";
        }
        basePath_ = basePath;
        baseUrl_ = new URL( server.getBaseUrl(), basePath );
        urlMap_ = Collections.synchronizedMap( new HashMap() );
    }

    /**
     * Returns the base URL for resources served by this handler.
     *
     * @return  base URL for output
     */
    public URL getBaseUrl() {
        return baseUrl_;
    }

    /**
     * Adds a local URL to the list of those which can be served by this
     * handler, and returns the public URL at which it will be available.
     *
     * @param   localUrl  URL readable within this JVM
     * @return  URL readable in principle by external agents with the same
     *          content as <code>localUrl</code>
     */
    public synchronized URL addLocalUrl( URL localUrl ) {

        // Get a name for the publicly visible URL, using the same as the
        // local URL if possible.  This is just for cosmetic purposes.
        String path = localUrl.getPath();
        int lastSlash = path.lastIndexOf( "/" );
        String name = lastSlash >= 0 && lastSlash < path.length() - 1
                    ? path.substring( lastSlash + 1 )
                    : "f";

        // Construct a new unique public URL at which this resource will
        // be made available.
        String relPath;
        URL mappedUrl; 
        try {
            relPath = ++resourceCount_ + "/" + name;
            mappedUrl = new URL( baseUrl_, relPath );
        }
        catch ( MalformedURLException e ) {
            try {
                relPath = resourceCount_ + "/" + "f";
                mappedUrl = new URL( baseUrl_, relPath );
            }
            catch ( MalformedURLException e2 ) {
                throw (AssertionError) new AssertionError().initCause( e2 );
            }
        }

        // Remember the mapping between the local URL and the public one.
        urlMap_.put( relPath, localUrl );

        // Return the public URL.
        return mappedUrl;
    }

    /**
     * Removes access to a resource which was publicised by a previous call
     * to {@link #addLocalUrl}.
     *
     * @param   url   result of previous call to <code>addLocalUrl</code>
     */
    public synchronized void removeServerUrl( URL url ) {
        String surl = url.toString();
        String sbase = baseUrl_.toString();
        if ( ! surl.startsWith( sbase ) ) {
            return;
        }
        String relPath = surl.substring( sbase.length() );
        urlMap_.remove( relPath );
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {

        // Determine the source URL from which the data will be obtained.
        String path = request.getUrl();
        if ( ! path.startsWith( basePath_ ) ) {
            return null;
        }
        String relPath = path.substring( basePath_.length() );
        if ( ! urlMap_.containsKey( relPath ) ) {
            return HttpServer.createErrorResponse( 404, "Not found" );
        }
        URL srcUrl = (URL) urlMap_.get( relPath );

        // Copy relevant information from this resource to the HTTP response.
        final URLConnection conn;
        try {
            conn = srcUrl.openConnection();
            conn.connect();
        }
        catch ( IOException e ) {
            return HttpServer.createErrorResponse( 404, "Not found", e );
        }
        String method = request.getMethod();
        try {
            Map hdrMap = new HashMap();
            String contentType = conn.getContentType();
            if ( contentType != null ) {
                hdrMap.put( "Content-Type", contentType );
            }
            int contentLength = conn.getContentLength();
            if ( contentLength >= 0 ) {
                hdrMap.put( "Content-Length",
                            Integer.toString( contentLength ) );
            }
            String contentEncoding = conn.getContentEncoding();
            if ( contentEncoding != null ) {
                hdrMap.put( "Content-Encoding", contentEncoding );
            }
            if ( "GET".equals( method ) ) {
                return new HttpServer.Response( 200, "OK", hdrMap ) {
                    public void writeBody( OutputStream out )
                            throws IOException {
                        DefaultServer.copy( conn.getInputStream(), out );
                    }
                };
            }
            else if ( "HEAD".equals( method ) ) {
                return new HttpServer.Response( 200, "OK", hdrMap ) {
                    public void writeBody( OutputStream out ) {
                    }
                };
            }
            else {
                return HttpServer
                      .createErrorResponse( 405, "Unsupported method" );
            }
        }
        catch ( Exception e ) {
            return HttpServer
                  .createErrorResponse( 500, "Internal server error", e );
        }
    }
}
