package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;

/**
 * Freestanding SampXmlRpcServerFactory implementation.
 * A new server object is returned each time, but this does not 
 * mean a new port opened each time.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
public class InternalServerFactory implements SampXmlRpcServerFactory {

    private InternalServer server_;

    public synchronized SampXmlRpcServer getServer() throws IOException {
        return new InternalServer();
    }
}
