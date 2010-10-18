package org.astrogrid.samp.httpd;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler implementation which implements dynamic resource provision.
 * HTTP HEAD and GET methods are implemented.
 *
 * @author   Mark Taylor
 * @since    7 Jan 2009
 */
public class ResourceHandler implements HttpServer.Handler {
    private final String basePath_;
    private final URL serverUrl_;
    private final Map resourceMap_;
    private int iRes_;

    private static Logger logger_ =
        Logger.getLogger( ResourceHandler.class.getName() );

    /** Dummy resource indicating a withdrawn item. */
    private static final ServerResource EXPIRED = new ServerResource() {
        public String getContentType() {
            throw new AssertionError();
        }
        public long getContentLength() {
            throw new AssertionError();
        }
        public void writeBody( OutputStream out ) {
            throw new AssertionError();
        }
    };

    /**
     * Constructor.
     *
     * @param   server   HTTP server
     * @param   basePath   path from server root beneath which all resources
     *                     provided by this handler will appear
     */
    public ResourceHandler( HttpServer server, String basePath ) {
        if ( ! basePath.startsWith( "/" ) ) {
            basePath = "/" + basePath;
        }
        if ( ! basePath.endsWith( "/" ) ) {
            basePath = basePath + "/";
        }
        basePath_ = basePath;
        serverUrl_ = server.getBaseUrl();
        resourceMap_ = new HashMap();
    }

    /**
     * Adds a resource to this server.
     *
     * @param   name   resource name, for cosmetic purposes only
     * @param   resource  resource to make available
     * @return   URL at which resource can be found
     */
    public synchronized URL addResource( String name,
                                         ServerResource resource ) {
        String path = basePath_ + Integer.toString( ++iRes_ ) + "/";
        if ( name != null ) {
            path += name;
        }
        resourceMap_.put( path, resource );
        try {
            URL url = new URL( serverUrl_, path );
            logger_.info( "Resource added: " + url );
            return new URL( serverUrl_, path );
        }
        catch ( MalformedURLException e ) {
            throw new AssertionError( "Unknown protocol http??" );
        }
    }

    /**
     * Removes a resource from this server.
     *
     * @param  url  URL returned by a previous addResource call
     */
    public synchronized void removeResource( URL url ) {
        String path = url.getPath();
        if ( resourceMap_.containsKey( path ) ) {
            logger_.info( "Resource expired: " + url );
            resourceMap_.put( path, EXPIRED );
        }
        else {
            throw new IllegalArgumentException( "Unknown URL to expire: "
                                              + url );
        }
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {
        String path = request.getUrl();
        if ( ! path.startsWith( basePath_ ) ) {
            return null;
        }
        final ServerResource resource =
            (ServerResource) resourceMap_.get( path );
        if ( resource == EXPIRED ) {
            return HttpServer.createErrorResponse( 410, "Gone" );
        }
        else if ( resource != null ) {
            Map hdrMap = new LinkedHashMap();
            hdrMap.put( "Content-Type", resource.getContentType() );
            long contentLength = resource.getContentLength();
            if ( contentLength >= 0 ) {
                hdrMap.put( "Content-Length", Long.toString( contentLength ) );
            }
            String method = request.getMethod();
            if ( method.equals( "HEAD" ) ) {
                return new HttpServer.Response( 200, "OK", hdrMap ) {
                    public void writeBody( OutputStream out ) {
                    }
                };
            }
            else if ( method.equals( "GET" ) ) {
                return new HttpServer.Response( 200, "OK", hdrMap ) {
                    public void writeBody( OutputStream out )
                            throws IOException {
                        resource.writeBody( out );
                    }
                };
            }
            else {
                return HttpServer
                      .create405Response( new String[] { "HEAD", "GET" } );
            }
        }
        else {
            return HttpServer.createErrorResponse( 404, "Not found" );
        }
    }
}
