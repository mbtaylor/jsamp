package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;

/**
 * Exception thrown when an XML document which is intended for XML-RPC
 * processing has the wrong format, for instance violates the XML-RPC spec.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
class XmlRpcFormatException extends IOException {

    /**
     * No-arg constructor.
     */
    public XmlRpcFormatException() {
        this( "Badly-formed XML-RPC request/response" );
    }

    /**
     * Constructor.
     *
     * @param   msg  message
     */
    public XmlRpcFormatException( String msg ) {
        super( msg );
    }
}
