package org.astrogrid.samp.xmlrpc.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Utilities for XML manipulations required by SAMP/XML-RPC.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
public class XmlUtils {

    private static Logger logger_ =
        Logger.getLogger( XmlUtils.class.getName() );

    /**
     * Private constructor prevents instantiation.
     */
    private XmlUtils() {
    }

    /**
     * Returns an array of all the Element children of a DOM node.
     *
     * @param  parent  parent node
     * @return  children array
     */
    public static Element[] getChildren( Node parent ) {
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
     * @throws  XmlRpcFormatException  if there is not exactly one child
     *             per element
     */
    public static Element getChild( Node parent ) throws XmlRpcFormatException {
        Element[] els = getChildren( parent );
        if ( els.length == 1 ) {
            return els[ 0 ];
        }
        else if ( els.length == 0 ) {
            throw new XmlRpcFormatException( "No child element of "
                                           + ((Element) parent).getTagName() );
        }
        else {
            throw new XmlRpcFormatException( "Multiple children of "
                                           + ((Element) parent).getTagName() );
        }
    }

    /**
     * Returns the single child element of a DOM node, which has a given
     * known name.
     *
     * @param   node  parent node
     * @param   tagName  child node name
     * @return  sole child element with name <code>tagName</code>
     * @throws  XmlRpcFormatException  if there is not exactly one child
     *             element or if it does not have name <code>tagName</code>
     */
    public static Element getChild( Node parent, String tagName )
            throws XmlRpcFormatException {
        Element child = getChild( parent );
        if ( ! tagName.equals( child.getTagName() ) ) {
            throw new XmlRpcFormatException( "Unexpected child of "
                                           + ((Element) parent).getTagName()
                                           + ": " + child.getTagName()
                                           + " is not " + tagName );
        }
        return child;
    }

    /**
     * Returns the text content of an element as a string.
     *
     * @param   el  parent node
     * @return   text content
     * @throws  XmlRpcFormatException  if content is not just text
     */
    public static String getTextContent( Element el ) throws
            XmlRpcFormatException {
        StringBuffer sbuf = new StringBuffer();
        for ( Node node = el.getFirstChild(); node != null;
              node = node.getNextSibling() ) {
            if ( node instanceof Text ) {
                sbuf.append( ((Text) node).getData() );
            }
            else if ( node instanceof Element ) {
                throw new XmlRpcFormatException( "Unexpected node " + node
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
    public static Object parseSampValue( Element valueEl )
            throws XmlRpcFormatException {
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
                    throw new XmlRpcFormatException(
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
                    throw new XmlRpcFormatException( "<name> missing"
                                                   + " in struct member" );
                }
                if ( value == null ) {
                    throw new XmlRpcFormatException( "<value> missing"
                                                   + " in struct member" );
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
                throw new XmlRpcFormatException( "Bad int " + text );
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
                throw new XmlRpcFormatException( "Bad boolean " + text );
            }
        }
        else if ( "double".equals( name ) ) {
            String text = getTextContent( el );
            try {
                return Double.valueOf( text );
            }
            catch ( NumberFormatException e ) {
                throw new XmlRpcFormatException( "Bad double " + text );
            }
        }
        else if ( "dateTime.iso8601".equals( name ) ||
                  "base64".equals( name ) ) {
            throw new XmlRpcFormatException( name + " not used in SAMP" );
        }
        else {
            throw new XmlRpcFormatException( "Unknown XML-RPC element "
                                           + "<" + name + ">" );
        }
    }
}
