package org.astrogrid.samp.xmlrpc.internal;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.httpd.UtilServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcHandler;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * SampXmlRpcServer implementation without external dependencies.
 *
 * @author   Mark Taylor
 * @since    27 Aug 2008
 */
public class InternalServer implements SampXmlRpcServer {

    private final HttpServer server_;
    private final URL endpoint_;
    private final List handlerList_;

    private static final Logger logger_ =
        Logger.getLogger( InternalServer.class.getName() );

    /**
     * Constructor based on a given HTTP server.
     * It is the caller's responsibility to configure and start the HttpServer.
     *
     * @param  httpServer  server for processing HTTP requests
     * @param  path   path part of server endpoint (starts with "/");
     */
    public InternalServer( HttpServer httpServer, final String path )
            throws IOException {
        server_ = httpServer;
        endpoint_ = new URL( server_.getBaseUrl(), path );
        handlerList_ = Collections.synchronizedList( new ArrayList() );
        server_.addHandler( new HttpServer.Handler() {
            public HttpServer.Response serveRequest( HttpServer.Request req ) {
                if ( req.getUrl().equals( path ) ) {
                    if ( req.getMethod().equals( "POST" ) ) {
                        return getXmlRpcResponse( req.getBody() );
                    }
                    else {
                        return HttpServer
                              .create405Response( new String[] { "POST" } );
                    }
                }
                else {
                    return null;
                }
            }
        } );
    }

    /**
     * Constructs a server running with default characteristics.
     * Currently, the default server 
     * {@link org.astrogrid.samp.httpd.UtilServer#getInstance} is used.
     */
    public InternalServer() throws IOException {
        this( UtilServer.getInstance().getServer(),
              UtilServer.getInstance().getBasePath( "/xmlrpc" ) );
    }

    public URL getEndpoint() {
        return endpoint_;
    }

    public void addHandler( SampXmlRpcHandler handler ) {
        handlerList_.add( handler );
    }

    public void removeHandler( SampXmlRpcHandler handler ) {
        handlerList_.remove( handler );
    }

    /**
     * Returns the HTTP response object given the body of an incoming
     * XML-RPC POST request.  Any error should be handled by returning
     * a fault-type methodResponse element rather than by throwing an
     * exception.
     *
     * @param  body  byte buffer containing POSTed body
     * @return  XML-RPC response (possibly fault)
     */
    protected HttpServer.Response getXmlRpcResponse( byte[] body ) {
        byte[] rbuf;
        try {
            rbuf = getResultBytes( getXmlRpcResult( body ) );
        }
        catch ( Throwable e ) {
            boolean isChecked = ! ( e instanceof RuntimeException ||
                                    e instanceof Error );
            logger_.log( isChecked ? Level.INFO : Level.WARNING,
                         "XML-RPC fault return", e );
            try {
                rbuf = getFaultBytes( e );
            }
            catch ( IOException e2 ) {
                return HttpServer.createErrorResponse( 500, "Server error",
                                                       e2 );
            }
        }
        final byte[] replyBuf = rbuf;
        Map hdrMap = new HashMap();
        hdrMap.put( "Content-Length", Integer.toString( replyBuf.length ) );
        hdrMap.put( "Content-Type", "text/xml" );
        return new HttpServer.Response( 200, "OK", hdrMap ) {
            public void writeBody( OutputStream out ) throws IOException {
                out.write( replyBuf );
            }
        };
    }

    /**
     * Returns the SAMP-friendly (string, list and map only) object representing
     * the reply to an XML-RPC request given by a supplied byte array.
     *
     * @param  body  byte buffer containing POSTed body
     * @return   SAMP-friendly object
     * @throws  Exception  in case of error (will become XML-RPC fault)
     */ 
    private Object getXmlRpcResult( byte[] body ) throws Exception {

        // Parse body as XML document.
        if ( body == null || body.length == 0 ) {
            throw new XmlRpcFormatException( "No body in POSTed request" );
        }
        Document doc = XmlUtils.createDocumentBuilder()
                      .parse( new ByteArrayInputStream( body ) );

        // Extract basic XML-RPC information from DOM.
        Element call = XmlUtils.getChild( doc, "methodCall" );
        String methodName = null;
        Element paramsEl = null;
        Element[] methodChildren = XmlUtils.getChildren( call );
        for ( int i = 0; i < methodChildren.length; i++ ) {
            Element el = methodChildren[ i ];
            String tagName = el.getTagName();
            if ( tagName.equals( "methodName" ) ) {
                methodName = XmlUtils.getTextContent( el );
            }
            else if ( tagName.equals( "params" ) ) {
                paramsEl = el;
            }
        }
        if ( methodName == null ) {
            throw new XmlRpcFormatException( "No methodName element" );
        }

        // Find one of the registered handlers to handle this request.
        SampXmlRpcHandler handler = null;
        SampXmlRpcHandler[] handlers =
            (SampXmlRpcHandler[])
            handlerList_.toArray( new SampXmlRpcHandler[ 0 ] );
        for ( int ih = 0; ih < handlers.length && handler == null; ih++ ) {
            SampXmlRpcHandler h = handlers[ ih ];
            if ( h.canHandleCall( methodName ) ) {
                handler = h;
            }
        }
        if ( handler == null ) {
            throw new XmlRpcFormatException( "Unknown XML-RPC method "
                                           + methodName );
        }

        // Extract parameter values from DOM.
        Element[] paramEls = paramsEl == null
                           ? new Element[ 0 ]
                           : XmlUtils.getChildren( paramsEl );
        int np = paramEls.length;
        List paramList = new ArrayList( np );
        for ( int i = 0; i < np; i++ ) {
            Element paramEl = paramEls[ i ];
            if ( ! "param".equals( paramEl.getTagName() ) ) {
                throw new XmlRpcFormatException( "Non-param child of params" );
            }
            else {
                Element valueEl = XmlUtils.getChild( paramEl, "value" );
                paramList.add( XmlUtils.parseSampValue( valueEl ) );
            }
        }

        // Pass the call to the handler and return the result.
        return handleCall( handler, methodName, paramList );
    }

    /**
     * Actually passes the XML-RPC method name and parameter list to one
     * of the registered servers for processing.
     *
     * @param   handler  handler which has declared it can handle the
     *                   named method
     * @param   methodName  XML-RPC method name
     * @param   paramList  list of parameters to XML-RPC call
     */
    protected Object handleCall( SampXmlRpcHandler handler, String methodName,
                                 List paramList ) throws Exception {
        return handler.handleCall( methodName, paramList );
    }

    /**
     * Turns a SAMP-friendly (string, list, map only) object into an array
     * of bytes giving an XML-RPC methodResponse document.
     *
     * @param  result  SAMP-friendly object
     * @return   XML methodResponse document as byte array
     */
    private byte[] getResultBytes( Object result ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedOutputStream bout = new BufferedOutputStream( out );
        XmlWriter xout = new XmlWriter( bout, 2 );
        xout.start( "methodResponse" );
        xout.start( "params" );
        xout.start( "param" );
        xout.sampValue( result );
        xout.end( "param" );
        xout.end( "params" );
        xout.end( "methodResponse" );
        xout.close();
        return out.toByteArray();
    }

    /**
     * Turns an exception into an array of bytes giving an XML-RPC 
     * methodResponse (fault) document.
     *
     * @param  error  throwable
     * @return   XML methodResponse document as byte array
     */
    private byte[] getFaultBytes( Throwable error ) throws IOException {
        int faultCode = 1;
        String faultString = error.toString();

        // Write the method response element.  We can't use the XmlWriter
        // sampValue method to do the grunt-work here since the faultCode
        // contains an <int>, which is not a known SAMP type.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedOutputStream bout = new BufferedOutputStream( out );
        XmlWriter xout = new XmlWriter( bout, 2 );
        xout.start( "methodResponse" );
        xout.start( "fault" );
        xout.start( "value" );
        xout.start( "struct" );
        xout.start( "member" );
        xout.inline( "name", "faultCode" );
        xout.start( "value" );
        xout.inline( "int", Integer.toString( faultCode ) );
        xout.end( "value" );
        xout.end( "member" );
        xout.start( "member" );
        xout.inline( "name", "faultString" );
        xout.inline( "value", faultString );
        xout.end( "member" );
        xout.end( "struct" );
        xout.end( "value" );
        xout.end( "fault" );
        xout.end( "methodResponse" );
        xout.close();
        return out.toByteArray();
    }
}
