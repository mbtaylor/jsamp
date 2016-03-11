package org.astrogrid.samp.httpd;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Handler implementation which allows the server to serve a directory
 * full of resources.
 * The {@link URLMapperHandler} class can sort of do the same thing,
 * but it's difficult to get the path right.
 *
 * <p>For if an instance is initialised as
 * <code>new DirectoryMapperHandler("/tmp/files", "/data")</code>
 * and installed on a server running at <code>http://localhost:8000/</code>,
 * then a server request for <code>http://localhost:8000/data/xxx</code>
 * would be fulfilled by returning the content of the file
 * <code>/tmp/files/xxx</code>.
 *
 * @author   Mark Taylor
 * @since    11 Mar 2016
 */
public class DirectoryMapperHandler implements HttpServer.Handler {

    private final String localDocBase_;
    private final String serverDocPath_;

    /**
     * Constructor.
     *
     * @param  localDocBase  prefix for resources available within the JVM
     * @param  serverDocPath  prefix for resources made available by this server
     */
    public DirectoryMapperHandler( String localDocBase, String serverDocPath ) {
        localDocBase_ = localDocBase;
        serverDocPath_ = serverDocPath;
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {
        String path = request.getUrl();
        if ( ! path.startsWith( serverDocPath_ ) ) {
            return null;
        }
        String relPath = path.substring( serverDocPath_.length() );
        final URL srcUrl = getClass().getResource( localDocBase_ + relPath );
        return srcUrl == null
             ? null
             : URLMapperHandler.mapUrlResponse( request.getMethod(), srcUrl );
    }
}
