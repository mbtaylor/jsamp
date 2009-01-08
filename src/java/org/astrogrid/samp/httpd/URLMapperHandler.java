package org.astrogrid.samp.httpd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler implementation which allows the server to serve resources which
 * are available to it as URLs.  The main use for this is if the URLs
 * are jar:-type ones which are available to the JVM in which the server
 * is running, but not to it's clients.
 * Either a single resource or a whole tree may be served.
 * 
 * @author   Mark Taylor
 * @since    8 Jan 2009
 */
public class URLMapperHandler implements HttpServer.Handler {
    private final String basePath_;
    private final URL sourceURL_;
    private final boolean includeRelatives_;

    /** Buffer size for copying data to response. */
    public static int BUFSIZ = 16 * 1024;

    /**
     * Constructor.
     *
     * @param   basePath   path of served resources relative to the base path
     *                     of the server itself
     * @param   sourceURL  URL of the resource which is to be made available
     *                     at the basePath by this handler
     * @param   includeRelatives  if true, relative URLs based at 
     *                     <code>basePath</code>
     *                     may be requested (potentially giving access to 
     *                     for instance the entire tree of classpath resources);
     *                     if false, only the exact resource named by 
     *                     <code>sourceURL</code> is served
     */
    public URLMapperHandler( String basePath, URL sourceURL,
                             boolean includeRelatives ) {
        basePath_ = basePath;
        sourceURL_ = sourceURL;
        includeRelatives_ = includeRelatives;
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {

        // Determine the source URL from which the data will be obtained.
        String path = request.getUrl();
        if ( ! path.startsWith( basePath_ ) ) {
            return null;
        }
        String relPath = path.substring( basePath_.length() );
        final URL srcUrl;
        if ( includeRelatives_ ) {
            try {
                srcUrl = new URL( sourceURL_, relPath );
            }
            catch ( MalformedURLException e ) {
                return HttpServer
                      .createErrorResponse( 500, "Internal server error", e );
            }
        }
        else {
            if ( relPath.length() == 0 ) {
                srcUrl = sourceURL_;
            }
            else {
                return HttpServer.createErrorResponse( 403, "Forbidden" );
            }
        }

        // Copy relevant information from this resource to the HTTP Response.
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
                        InputStream in = conn.getInputStream();
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
