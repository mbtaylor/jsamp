package org.astrogrid.samp.xmlrpc.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Represents the content of an XML-RPC methodCall element.
 *
 * @author   Mark Taylor
 * @since    11 Mar 2016
 * @see      <a href="http://www.xmlrpc.com/">XML-RPC</a>
 */
public class XmlRpcCall {

    private final String methodName_;
    private final List params_;

    /**
     * Constructor.
     *
     * @param   methodName  content of <code>methodName</code> element
     * @param   params   SAMP-friendly list of parameters as contained
     *                   in the <code>params</code> element
     */
    public XmlRpcCall( String methodName, List params ) {
        methodName_ = methodName;
        params_ = Collections.unmodifiableList( params );
    }

    /**
     * Returns the method name.
     *
     * @return  content of <code>methodName</code> element
     */
    public String getMethodName() {
        return methodName_;
    }

    /**
     * Returns the parameter list.
     *
     * @return  SAMP-friendly list of parameter values from
     *          <code>params</code> element
     */
    public List getParams() {
        return params_;
    }

    /**
     * Constructs an XmlRpcCall instance from a document with a
     * methodCall element at top level.
     *
     * @param  callDoc  node whose child is an XML-RPC
     *         <code>methodCall</code> element
     * @return   call instance
     * @throws   XmlRpcFormatException   if the document does not have the
     *                                   expected form
     */
    public static XmlRpcCall createCall( Document callDoc )
            throws XmlRpcFormatException {

        // Get expected top-level element.
        Element callEl = XmlUtils.getChild( callDoc, "methodCall" );

        // Get methodName and params elements.
        String methodName = null;
        Element paramsEl = null;
        Element[] methodChildren = XmlUtils.getChildren( callEl );
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

        // Extract parameter values as list.
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

        // Construct and return call.
        return new XmlRpcCall( methodName, paramList );
    }
}
