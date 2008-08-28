package org.astrogrid.samp.xmlrpc.internal;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import org.astrogrid.samp.SampUtils;
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

    private static final String PATH = "/xmlrpc";
    private static final Logger logger_ =
        Logger.getLogger( InternalServer.class.getName() );

    /**
     * Constructor based on a given HTTP server.
     * It is the caller's responsibility to configure and start the HttpServer.
     *
     * @param  httpServer  server for processing HTTP requests
     */
    public InternalServer( HttpServer httpServer ) throws IOException {
        server_ = httpServer;
        endpoint_ = new URL( server_.getBaseUrl(), PATH );
        handlerList_ = new ArrayList();
        server_.addHandler( new HttpServer.Handler() {
            public HttpServer.Response serveRequest( HttpServer.Request req ) {
                if ( req.getUrl().equals( PATH ) &&
                     req.getMethod().equals( "POST" ) ) {
                    return getXmlRpcResponse( req.getBody() );
                }
                else {
                    return null;
                }
            }
        } );
    }

    /**
     * Constructs a server running with default characteristics 
     * on any free port.  The server is started as a daemon thread.
     */
    public InternalServer() throws IOException {
        this( new HttpServer() );
        server_.setDaemon( true );
        server_.start();
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
    private HttpServer.Response getXmlRpcResponse( byte[] body ) {
        byte[] rbuf;
        try {
            rbuf = getResultBytes( getXmlRpcResult( body ) );
        }
        catch ( Exception e ) {
            logger_.log( Level.INFO, "XML-RPC fault return", e );
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
            protected void writeBody( OutputStream out ) throws IOException {
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
        Document doc = DocumentBuilderFactory.newInstance()
                      .newDocumentBuilder()
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
        for ( Iterator it = handlerList_.iterator();
              it.hasNext() && handler == null; ) {
            SampXmlRpcHandler h = (SampXmlRpcHandler) it.next();
            if ( h.canHandleCall( methodName ) ) {
                handler = h;
            }
        }
        if ( handler == null ) {
            return new XmlRpcFormatException( "Unknown XML-RPC method "
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
                return new XmlRpcFormatException( "Non-param child of params" );
            }
            else {
                Element valueEl = XmlUtils.getChild( paramEl, "value" );
                paramList.add( XmlUtils.parseSampValue( valueEl ) );
            }
        }

        // Pass the call to the handler and return the result.
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedOutputStream bout = new BufferedOutputStream( out );
        XmlWriter xout = new XmlWriter( bout, 2 );
        Map faultMap = new HashMap();
        faultMap.put( "faultCode", "1" );
        faultMap.put( "faultString", error.toString() );
        xout.start( "methodResponse" );
        xout.start( "fault" );
        xout.sampValue( faultMap );
        xout.end( "fault" );
        xout.end( "methodResponse" );
        xout.close();
        return out.toByteArray();
    }
}
