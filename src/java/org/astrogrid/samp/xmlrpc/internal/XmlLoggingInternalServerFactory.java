package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;

/**
 * Freestanding ServerFactory implementation which logs all incoming
 * and outgoing HTTP data.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */
public class XmlLoggingInternalServerFactory
        implements SampXmlRpcServerFactory {

    private XmlLoggingInternalServer server_;

    public synchronized SampXmlRpcServer getServer() throws IOException {
        if ( server_ == null ) {
            server_ = new XmlLoggingInternalServer( System.out );
        }
        return server_;
    }
}
