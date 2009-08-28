package org.astrogrid.samp.test;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.xmlrpc.internal.InternalClient;
import org.astrogrid.samp.xmlrpc.internal.XmlUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * SampXmlrpcClient implementation for testing success or failure of 
 * XML-RPC method invocations.
 * Note the return value of the {@link #callAndWait} method is either
 * {@link #SUCCESS} or {@link #FAILURE}, rather than the actual result
 * of the call.
 *
 * Methods may throw {@link TestException}s to indicate assertion failures.
 *
 * @author   Mark Taylor
 * @since    28 Aug 2009
 */
class TestXmlrpcClient extends InternalClient {

    public static final Object SUCCESS = "Success";
    public static final Object FAILURE = "Failure";

    /**
     * Constructor.
     *
     * @param  endpoint   hub HTTP endpoint
     */
    TestXmlrpcClient( URL endpoint ) {
        super( endpoint );
    }

    /**
     * Returns {@link #SUCCESS} or {@link #FAILURE}.
     */
    protected Object deserializeResponse( InputStream in )
            throws IOException {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                          .newDocumentBuilder().parse( in );
            Element topEl = XmlUtils.getChild( doc, "methodResponse" );
            Element contentEl = XmlUtils.getChild( topEl );
            String contentTag = contentEl.getTagName();
            if ( "params".equals( contentTag ) ) {
                Element paramEl = XmlUtils.getChild( contentEl, "param" ); 
                Element valueEl = XmlUtils.getChild( paramEl, "value" );
                Object value = XmlUtils.parseSampValue( valueEl );
                SampUtils.checkObject( value );
                return SUCCESS;
            }
            else if ( "fault".equals( contentTag ) ) {
                Element valueEl = XmlUtils.getChild( contentEl, "value" );
                Map value = (Map) XmlUtils.parseSampValue( valueEl );
                String faultString = (String) value.get( "faultString" );
                int faultCode = ((Integer) value.get( "faultCode" )).intValue();
                Tester.assertEquals( 2, value.size() );
                return FAILURE;
            }
            else {
                throw new TestException( "Unknown <methodResponse> child: "
                                       + contentTag );
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
     * Makes a call, and asserts that the result is a normal XML-RPC 
     * response.
     *
     * @param  method    XML-RPC method name
     * @param  params    parameters for XML-RPC call (SAMP-compatible)
     * @throws  TestException   if the response is not a success
     */
    public void checkSuccessCall( String method, List params )
            throws IOException {
        Tester.assertEquals( SUCCESS, callAndWait( method, params ) );
    }

    /**
     * Makes a call, and asserts that the result is an XML-RPC fault.
     *
     * @param  method    XML-RPC method name
     * @param  params    parameters for XML-RPC call (SAMP-compatible)
     * @throws  TestException  if the response is not a fault
     */
    public void checkFailureCall( String method, List params )
            throws IOException {
        Tester.assertEquals( FAILURE, callAndWait( method, params ) );
    }
}
