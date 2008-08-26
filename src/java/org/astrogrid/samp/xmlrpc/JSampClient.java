package org.astrogrid.samp.xmlrpc;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

/**
 * XML-RPC client implementation suitable for use with SAMP.
 * This implementation is completely freestanding and requires no other
 * libraries.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
public class JSampClient implements SampXmlRpcClient {

    private static final String ENCODING = "UTF-8";
    private static final Logger logger_ =
        Logger.getLogger( SampXmlRpcClient.class.getName() );

    public Object callAndWait( URL endpoint, String method, List params )
            throws IOException {
        HttpURLConnection connection = 
            (HttpURLConnection) endpoint.openConnection();
        byte[] callBuf = serializeCall( method, params );
        connection.setDoOutput( true );
        connection.setDoInput( true );
        connection.setRequestMethod( "POST" );
        connection.setRequestProperty( "Content-Type", "text/xml" );
        connection.setRequestProperty( "Content-Length",
                                       Integer.toString( callBuf.length ) );
        connection.connect();
        OutputStream out = connection.getOutputStream();
        out.write( callBuf );
        out.flush();
        InputStream in = new BufferedInputStream( connection.getInputStream() );
        Object result = deserializeResponse( in );
        connection.disconnect();
        return result;
    }

    public void callAndForget( URL endpoint, String method, List params )
            throws IOException {
        final HttpURLConnection connection =
            (HttpURLConnection) endpoint.openConnection();
        byte[] callBuf = serializeCall( method, params );
        connection.setDoOutput( true );
        connection.setDoInput( true );
        connection.setRequestMethod( "POST" );
        connection.setRequestProperty( "Content-Type", "text/xml" );
        connection.setRequestProperty( "Content-Length",
                                       Integer.toString( callBuf.length ) );
        connection.connect();
        OutputStream out = connection.getOutputStream();
        out.write( callBuf );
        out.flush();
  
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
    private static byte[] serializeCall( String method, List paramList )
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer writer =
            new BufferedWriter( new OutputStreamWriter( bos, ENCODING ) );
        XmlWriter xout = new XmlWriter( writer, 2 );
        xout.literal( "<?xml version='1.0' encoding='" + ENCODING + "'?>\n" );
        xout.start( "methodCall" );
        xout.inline( "methodName", method );
        if ( ! paramList.isEmpty() ) {
            xout.start( "params" );
            for ( Iterator it = paramList.iterator(); it.hasNext(); ) {
                xout.start( "param" );
                serializeValue( xout, it.next() );
                xout.end( "param" );
            }
            xout.end( "params" );
        }
        xout.end( "methodCall" );
        writer.close();
        return bos.toByteArray();
    }

    /**
     * Serializes an XML-RPC <code>value</code> element corresponding to a 
     * supplied SAMP-friendly object.
     *
     * @param  xout  destination xml writer object 
     * @param  value  object to serialize; must be a string, list or map
     */
    private static void serializeValue( XmlWriter xout, Object value )
            throws IOException {
        if ( value instanceof String ) {
            xout.inline( "value", (String) value );
        }
        else if ( value instanceof List ) {
            xout.start( "value" );
            xout.start( "array" );
            xout.start( "data" );
            for ( Iterator it = ((List) value).iterator(); it.hasNext(); ) {
                serializeValue( xout, it.next() );
            }
            xout.end( "data" );
            xout.end( "array" );
            xout.end( "value" );
        }
        else if ( value instanceof Map ) {
            xout.start( "value" );
            xout.start( "struct" );
            for ( Iterator it = ((Map) value).entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                xout.start( "member" );
                xout.inline( "name", entry.getKey().toString() );
                serializeValue( xout, entry.getValue() );
                xout.end( "member" );
            }
            xout.end( "struct" );
            xout.end( "value" );
        }
    }

    /**
     * Deserializes an XML-RPC <code>methodResponse</code> document to a
     * Java object.
     *
     * @param   in  input stream containing response document
     */
    private static Object deserializeResponse( InputStream in )
            throws IOException {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                          .newDocumentBuilder().parse( in );
            Element top = getChild( getChild( doc, "methodResponse" ) );
            String topName = top.getTagName();
            if ( "fault".equals( topName ) ) {
                Element value = getChild( top, "value" );
                getChild( value, "struct" );
                Map faultMap = (Map) parseSampValue( value );
                Object fcode = faultMap.get( "faultCode" );
                Object fmsg = faultMap.get( "faultString" );
                int code = fcode instanceof Integer
                         ? ((Integer) fcode).intValue()
                         : -9999;
                final String msg = String.valueOf( fmsg );
                throw new XmlRpcFault( code, msg );
            }
            else if ( "params".equals( topName ) ) {
                Element value = getChild( getChild( top, "param" ), "value" );
                return parseSampValue( value );
            }
            else {
                throw new XmlRpcResponseParseException(
                     "Not <fault> or <params>?" );
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
     * Returns an array of all the Element children of a DOM node.
     *
     * @param  parent  parent node
     * @return  children array
     */
    private static Element[] getChildren( Node parent ) {
        NodeList nodeList = parent.getChildNodes();
        int nnode = nodeList.getLength();
        List elList = new ArrayList( nnode );
        for ( int i = 0; i < nnode; i++ ) {
            Node node = nodeList.item( i );
            if ( node instanceof Element ) {
                elList.add( (Element) node );
            }
        }
        return (Element[]) elList.toArray( new Element[ 0 ] );
    }

    /**
     * Returns the single element child of a DOM node.
     *
     * @param  node  parent node
     * @return   sole child element
     * @throws  XmlRpcResponseParseException  if there is not exactly one child
     *             element
     */
    private static Element getChild( Node parent )
            throws XmlRpcResponseParseException {
        Element[] els = getChildren( parent );
        if ( els.length == 1 ) {
            return els[ 0 ];
        }
        else if ( els.length == 0 ) {
            throw new XmlRpcResponseParseException( "No child element of "
                                 + ((Element) parent).getTagName() );
        }
        else {
            throw new XmlRpcResponseParseException( "Multiple children of "
                                 + ((Element) parent).getTagName() );
        }
    }

    /**
     * Returns the single child eleemnt of a DOM node, which has a given
     * known name.
     *
     * @param   node  parent node
     * @param   tagName  child node name
     * @return  sole child element with name <code>tagName</code>
     * @throws  XmlRpcResponseParseException  if there is not exactly one child
     *             element or if it does not have name <code>tagName</code>
     */
    private static Element getChild( Node parent, String tagName )
            throws XmlRpcResponseParseException {
        Element child = getChild( parent );
        if ( ! tagName.equals( child.getTagName() ) ) {
            throw new XmlRpcResponseParseException( "Unexpected child of "
                                                  + ((Element) parent)
                                                   .getTagName() + ": "
                                                  + child.getTagName()
                                                  + " is not " + tagName );
        }
        return child;
    }

    /**
     * Returns the text content of an element as a string.
     *
     * @param   el  parent node
     * @return   text content
     * @throws  XmlRpcResponseParseException  if content is not just text
     */
    private static String getTextContent( Element el )
            throws XmlRpcResponseParseException {
        StringBuffer sbuf = new StringBuffer();
        for ( Node node = el.getFirstChild(); node != null; 
              node = node.getNextSibling() ) {
            if ( node instanceof Text ) {
                sbuf.append( ((Text) node).getData() );
            }
            else if ( node instanceof Element ) {
                throw new XmlRpcResponseParseException( "Unexpected node " 
                                                      + node
                                                      + " in " + el.getTagName()
                                                      + " content" );
            }
        }
        return sbuf.toString();
    }

    /**
     * Returns the content of a DOM element representing a <code>value</code>
     * element of an XML-RPC document.
     * Note that some content which would be legal in XML-RPC, but is not
     * legal in SAMP, may result in an exception.
     *
     * @param   valueEl   value element
     * @return  SAMP-friendly object (string, list or map)
     */
    private static Object parseSampValue( Element valueEl )
            throws XmlRpcResponseParseException {
        if ( getChildren( valueEl ).length == 0 ) {
            return getTextContent( valueEl );
        }
        Element el = getChild( valueEl );
        String name = el.getTagName();
        if ( "array".equals( name ) ) {
            Element[] valueEls = getChildren( getChild( el, "data" ) );
            int nel = valueEls.length;
            List list = new ArrayList( nel );
            for ( int i = 0; i < nel; i++ ) {
                list.add( parseSampValue( valueEls[ i ] ) );
            }
            return list;
        }
        else if ( "struct".equals( name ) ) {
            Element[] memberEls = getChildren( el );
            Map map = new HashMap();
            for ( int i = 0; i < memberEls.length; i++ ) {
                Element member = memberEls[ i ];
                if ( ! "member".equals( member.getTagName() ) ) {
                    throw new XmlRpcResponseParseException(
                        "Non-<member> child of <struct>: "
                       + member.getTagName() );
                }
                Element[] memberChildren = getChildren( member );
                String key = null;
                Object value = null;
                for ( int j = 0; j < memberChildren.length; j++ ) {
                    Element memberChild = memberChildren[ j ];
                    String memberName = memberChild.getTagName();
                    if ( "name".equals( memberName ) ) {
                        key = getTextContent( memberChild );
                    }
                    else if ( "value".equals( memberName ) ) {
                        value = parseSampValue( memberChild );
                    }
                }
                if ( key == null ) {
                    throw new XmlRpcResponseParseException(
                        "<name> missing in struct member" );
                }
                if ( value == null ) {
                    throw new XmlRpcResponseParseException(
                        "<value> missing in struct member" );
                }
                if ( map.containsKey( key ) ) {
                    logger_.warning( "Re-used key " + key + " in map" );
                }
                map.put( key, value );
            }
            return map;
        }
        else if ( "string".equals( name ) ) {
            return getTextContent( el );
        }
        else if ( "i4".equals( name ) || "int".equals( name ) ) {
            String text = getTextContent( el );
            try {
                return Integer.valueOf( text );
            }
            catch ( NumberFormatException e ) {
                throw new XmlRpcResponseParseException( "Bad int " + text );
            }
        }
        else if ( "boolean".equals( name ) ) {
            String text = getTextContent( el );
            if ( "0".equals( text ) ) {
                return Boolean.FALSE;
            }
            else if ( "1".equals( text ) ) {
                return Boolean.TRUE;
            }
            else {
                throw new XmlRpcResponseParseException( "Bad boolean " + text );
            }
        }
        else if ( "double".equals( name ) ) {
            String text = getTextContent( el );
            try {
                return Double.valueOf( text );
            }
            catch ( NumberFormatException e ) {
                throw new XmlRpcResponseParseException( "Bad double " + text );
            }
        }
        else if ( "dateTime.iso8601".equals( name ) ||
                  "base64".equals( name ) ) {
            throw new XmlRpcResponseParseException( name
                                                  + " not used in SAMP" );
        }
        else {
            throw new XmlRpcResponseParseException( "Unknown XML-RPC element "
                                                  + "<" + name + ">" );
        }
    }

    /**
     * IOException representing an incoming XML-RPC fault.
     */
    private static class XmlRpcFault extends IOException {
        public XmlRpcFault( int code, String msg ) {
            super( code + ": " + msg );
        }
    }

    /**
     * IOException indicating a badly-formed XML-RPC response document.
     */
    private static class XmlRpcResponseParseException extends IOException {
        public XmlRpcResponseParseException( String msg ) {
            super( msg );
        }
        public XmlRpcResponseParseException() {
            this( "Badly formed XML-RPC response" );
        }
    }

    /**
     * Utility class for writing XML.
     */
    private static class XmlWriter {
        private final Writer out_;
        private final int indent_;
        private int iLevel_;

        /**
         * Constructor.
         *
         * @param   out  destination stream
         * @param   indent  number of spaces to indent each element level
         */
        XmlWriter( Writer out, int indent ) {
            out_ = out;
            indent_ = indent;
        }

        /**
         * Start an element.
         *
         * @param  element  tag name
         */
        public void start( String element ) throws IOException {
            pad( iLevel_++ );
            out_.write( '<' );
            out_.write( element );
            out_.write( '>' );
            newline();
        }

        /**
         * End an element.
         *
         * @param  element  tag name
         */
        public void end( String element ) throws IOException {
            pad( --iLevel_ );
            out_.write( "</" );
            out_.write( element );
            out_.write( '>' );
            newline();
        }

        /**
         * Write an element and its text content.
         *
         * @param  element  tag name
         * @param  content  element text content
         */
        public void inline( String element, String content )
                throws IOException {
            pad( iLevel_ );
            out_.write( '<' );
            out_.write( element );
            out_.write( '>' );
            text( content );
            out_.write( "</" );
            out_.write( element );
            out_.write( '>' );
            newline();
        }

        /**
         * Writes text.  Any escaping required for XML output will be 
         * taken care of.
         *
         * @param   txt   text to output
         */
        public void text( String txt ) throws IOException {
            int leng = txt.length();
            for ( int i = 0; i < leng; i++ ) {
                char c = txt.charAt( i );
                switch ( c ) {
                    case '&':
                        out_.write( "&amp;" );
                        break;
                    case '<':
                        out_.write( "&lt;" );
                        break;
                    case '>':
                        out_.write( "&gt;" );
                        break;
                    default:
                        out_.write( c );
                }
            }
        }

        /**
         * Writes text with no escaping of XML special characters etc. 
         *
         * @param  txt  raw text to output
         */
        public void literal( String txt ) throws IOException {
            out_.write( txt );
        }

        /**
         * Writes a new line character.
         */
        public void newline() throws IOException {
            out_.write( '\n' );
        }

        /**
         * Outputs start-of-line padding for a given level of indentation.
         *
         * @param  level  level of XML element ancestry 
         */
        private void pad( int level ) throws IOException {
            int npad = level * indent_;
            for ( int i = 0; i < npad; i++ ) {
                out_.write( ' ' );
            }
        }
    }
}
