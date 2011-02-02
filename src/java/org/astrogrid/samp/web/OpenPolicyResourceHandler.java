package org.astrogrid.samp.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.httpd.ServerResource;

/**
 * HTTP resource handler suitable for serving static cross-origin policy files.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class OpenPolicyResourceHandler implements HttpServer.Handler {

    private final String policyPath_;
    private final ServerResource policyResource_;
    private final OriginAuthorizer authorizer_;
    private final HttpServer.Response response405_;

    /**
     * Constructor.
     *
     * @param   policyPath  path at which the policy file will reside on
     *          this handler's server
     * @param   policyResource  content of policy file
     * @param   authorizer  controls who is permitted to view the policy file
     */
    public OpenPolicyResourceHandler( String policyPath,
                                      ServerResource policyResource,
                                      OriginAuthorizer authorizer ) {
        policyPath_ = policyPath;
        policyResource_ = policyResource;
        authorizer_ = authorizer;
        response405_ =
            HttpServer.create405Response( new String[] { "GET", "HEAD", } );
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {
        if ( request.getUrl().equals( policyPath_ ) ) {
            String method = request.getMethod();
            if ( ! method.equals( "HEAD" ) &&
                 ! method.equals( "GET" ) ) {
                return response405_;
            }
            else if ( authorizer_.authorizeAll() ) {
                Map hdrMap = new LinkedHashMap();
                hdrMap.put( "Content-Type", policyResource_.getContentType() );
                long contentLength = policyResource_.getContentLength();
                if ( contentLength >= 0 ) {
                    hdrMap.put( "Content-Length",
                                Long.toString( contentLength ) );
                }
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
                            policyResource_.writeBody( out );
                        }
                    };
                }
                else {
                    assert false;
                    return response405_;
                }
            }
            else {
                return HttpServer.createErrorResponse( 404, "Not found" );
            }
        }
        else {
            return null;
        }
    }

    /**
     * Creates a handler suitable for serving static cross-origin policy files.
     *
     * @param  path  path at which the policy file will reside on the
     *               handler's HTTP server
     * @param  contentUrl  external URL at which the resource contents
     *                     can be found; this will be retrieved once and
     *                     cached
     * @param  oAuth  controls who is permitted to retrieve the policy file
     */
    public static HttpServer.Handler
                  createPolicyHandler( String path, URL contentUrl,
                                       String contentType,
                                       OriginAuthorizer oAuth )
            throws IOException {
        ServerResource resource =
            createCachedResource( contentUrl, contentType );
        return new OpenPolicyResourceHandler( path, resource, oAuth );
    }

    /**
     * Returns a handler which can serve the /crossdomain.xml file
     * used by Adobe Flash.  The policy file permits access from anywhere.
     *
     * @param  oAuth  controls who is permitted to retrieve the policy file
     * @return  policy file handler
     * @see  <a href="http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html"
     *          >Adobe Flash cross-origin policy</a>
     */
    public static HttpServer.Handler
                  createFlashPolicyHandler( OriginAuthorizer oAuth )
            throws IOException {
        return createPolicyHandler( "/crossdomain.xml",
                                    OpenPolicyResourceHandler.class
                                   .getResource( "crossdomain.xml" ),
                                    "text/x-cross-domain-policy", oAuth );
    }

    /**
     * Returns a handler which can serve the /clientaccesspolicy.xml file
     * used by Microsoft Silverlight.  The policy file permits access
     * from anywhere.
     *
     * @param  oAuth  controls who is permitted to retrieve the policy file
     * @return  policy file handler
     * @see  <a href="http://msdn.microsoft.com/en-us/library/cc645032(VS.95).aspx"
     *          >MS Silverlight cross-origin policy</a>
     */
    public static HttpServer.Handler
                  createSilverlightPolicyHandler( OriginAuthorizer oAuth )
            throws IOException {
        return createPolicyHandler( "/clientaccesspolicy.xml",
                                    OpenPolicyResourceHandler.class
                                   .getResource( "clientaccesspolicy.xml" ),
                                    "text/xml", oAuth );
    }         

    /**
     * Returns a ServerResource which caches the contents of a given
     * (presumably smallish and unchanging) external resource.
     *
     * @param  dataUrl  location of external resource
     * @param  contentType  MIME type for content of dataUrl
     * @return   new cached resource representing content of
     *           <code>dataUrl</code>
     */
    private static ServerResource
            createCachedResource( URL dataUrl, final String contentType )
                throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        InputStream uin = dataUrl.openStream();
        byte[] buf = new byte[ 1024 ];
        for ( int count; ( count = uin.read( buf ) ) >= 0; ) {
            bout.write( buf, 0, count );
        }
        bout.close();
        final byte[] data = bout.toByteArray();
        return new ServerResource() {
            public long getContentLength() {
                return data.length;
            }
            public String getContentType() {
                return contentType;
            }
            public void writeBody( OutputStream out ) throws IOException {
                out.write( data );
            }
        };
    }
}
