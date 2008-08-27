package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;

/**
 * Freestanding SampXmlRpcServerFactory implementation.
 * Server construction is lazy and the same server is returned each time.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
public class InternalServerFactory implements SampXmlRpcServerFactory {

    private InternalServer server_;

    public synchronized SampXmlRpcServer getServer() throws IOException {
        if ( server_ == null ) {
            server_ = new InternalServer();
        }
        return server_;
    }
}
