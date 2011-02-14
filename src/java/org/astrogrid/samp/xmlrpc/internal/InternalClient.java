package org.astrogrid.samp.xmlrpc.internal;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;

/**
 * XML-RPC client implementation suitable for use with SAMP.
 * This implementation is completely freestanding and requires no other
 * libraries.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
public class InternalClient implements SampXmlRpcClient {

    private final URL endpoint_;
    private final String userAgent_;
    private static final Logger logger_ =
        Logger.getLogger( InternalClient.class.getName() );

    /**
     * Constructor.
     *
     * @param  endpoint  endpoint
     */
    public InternalClient( URL endpoint ) {
        endpoint_ = endpoint;
        userAgent_ = "JSAMP/" + SampUtils.getSoftwareVersion();
    }

    public Object callAndWait( String method, List params )
            throws IOException {
        HttpURLConnection connection =
            (HttpURLConnection) endpoint_.openConnection();
        byte[] callBuf = serializeCall( method, params );
        connection.setDoOutput( true );
        connection.setDoInput( true );
        connection.setRequestMethod( "POST" );
        connection.setRequestProperty( "Content-Type", "text/xml" );
        connection.setRequestProperty( "Content-Length",
                                       Integer.toString( callBuf.length ) );
        connection.setRequestProperty( "User-Agent", userAgent_ );
        connection.connect();
        OutputStream out = connection.getOutputStream();
        out.write( callBuf );
        out.flush();
        out.close();
        int responseCode = connection.getResponseCode();
        if ( responseCode != HttpURLConnection.HTTP_OK ) {
            throw new IOException( responseCode + " "
                                 + connection.getResponseMessage() );
        }
        InputStream in = new BufferedInputStream( connection.getInputStream() );
        Object result = deserializeResponse( in );
        connection.disconnect();
        return result;
    }

    // NOTE: if this method is invoked from a shutdownHook thread,
    // the call may not complete because it is completed from a new thread.
    public void callAndForget( String method, List params )
            throws IOException {
        final HttpURLConnection connection =
            (HttpURLConnection) endpoint_.openConnection();
        byte[] callBuf = serializeCall( method, params );
        connection.setDoOutput( true );
        connection.setDoInput( true );
        connection.setRequestMethod( "POST" );
        connection.setRequestProperty( "Content-Type", "text/xml" );
        connection.setRequestProperty( "Content-Length",
                                       Integer.toString( callBuf.length ) );
        connection.setRequestProperty( "User-Agent", userAgent_ );
        connection.connect();
        OutputStream out = connection.getOutputStream();
        out.write( callBuf );
        out.flush();
        out.close();

        // It would be nice to just not read the input stream at all.
        // However, connection.setDoInput(false) and doing no reads causes
        // trouble - probably the call doesn't complete at the other end or
        // something.  So read it to the end asynchronously.
        new Thread() {
            public void run() {
                try {
                    InputStream in =
                        new BufferedInputStream( connection.getInputStream() );
                    while ( in.read() >= 0 ) {}
                    int responseCode = connection.getResponseCode();
                    if ( responseCode != HttpURLConnection.HTTP_OK ) {
                        logger_.warning( responseCode + " " +
                                         connection.getResponseMessage() );
                    }
                }
                catch ( IOException e ) {
                }
                finally {
                    connection.disconnect();
                }
            }
        }.start();
    }

    /**
     * Generates the XML <code>methodCall</code> document corresponding
     * to an XML-RPC method call.
     *
     * @param   method  methodName  string
     * @param   paramList  list of XML-RPC parameters
     * @return   XML document as byte array
     */
    protected byte[] serializeCall( String method, List paramList )
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XmlWriter xout = new XmlWriter( bos, 2 );
        xout.start( "methodCall" );
        xout.inline( "methodName", method );
        if ( ! paramList.isEmpty() ) {
            xout.start( "params" );
            for ( Iterator it = paramList.iterator(); it.hasNext(); ) {
                xout.start( "param" );
                xout.sampValue( it.next() );
                xout.end( "param" );
            }
            xout.end( "params" );
        }
        xout.end( "methodCall" );
        xout.close();
        return bos.toByteArray();
    }

    /**
     * Deserializes an XML-RPC <code>methodResponse</code> document to a
     * Java object.
     *
     * @param   in  input stream containing response document
     */
    protected Object deserializeResponse( InputStream in )
            throws IOException {
        try {
            Document doc = XmlUtils.createDocumentBuilder().parse( in );
            Element top =
                XmlUtils.getChild( XmlUtils.getChild( doc, "methodResponse" ) );
            String topName = top.getTagName();
            if ( "fault".equals( topName ) ) {
                Element value = XmlUtils.getChild( top, "value" );
                XmlUtils.getChild( value, "struct" );
                Map faultMap = (Map) XmlUtils.parseSampValue( value );
                Object fcode = faultMap.get( "faultCode" );
                Object fmsg = faultMap.get( "faultString" );
                int code = fcode instanceof Integer
                         ? ((Integer) fcode).intValue()
                         : -9999;
                final String msg = String.valueOf( fmsg );
                throw new XmlRpcFault( code, msg );
            }
            else if ( "params".equals( topName ) ) {
                Element value =
                    XmlUtils.getChild( XmlUtils.getChild( top, "param" ),
                                       "value" );
                return XmlUtils.parseSampValue( value );
            }
            else {
                throw new XmlRpcFormatException( "Not <fault> or <params>?" );
            }
        }
        catch ( ParserConfigurationException e ) {
            throw (IOException) new IOException( "Trouble with XML parsing" )
                               .initCause( e );
        }
        catch ( SAXException e ) {
            throw (IOException) new IOException( "Trouble with XML parsing" )
                               .initCause( e );
        }
        catch ( DOMException e ) {
            throw (IOException) new IOException( "Trouble with XML parsing" )
                               .initCause( e );
        }
    }

    /**
     * IOException representing an incoming XML-RPC fault.
     */
    private static class XmlRpcFault extends IOException {
        public XmlRpcFault( int code, String msg ) {
            super( "XML-RPC Fault (" + code + ": " + msg + ")" );
        }
    }
}
