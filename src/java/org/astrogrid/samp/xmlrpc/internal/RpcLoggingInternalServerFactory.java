package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;

/**
 * Freestanding ServerFactory implementation which logs all XML-RPC
 * calls/responses to standard output.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */ 
public class RpcLoggingInternalServerFactory
        implements SampXmlRpcServerFactory {
    private RpcLoggingInternalServer server_;

    public synchronized SampXmlRpcServer getServer() throws IOException {
        if ( server_ == null ) {
            server_ = new RpcLoggingInternalServer( System.out );
        }
        return server_;
    }
}
