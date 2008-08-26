package org.astrogrid.samp.xmlrpc;

import java.io.IOException;

/**
 * SampXmlRpcServerFactory implementation which uses Apache classes.
 * Server construction is lazy and the same server is returned each time.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public class ApacheServerFactory implements SampXmlRpcServerFactory {
    private SampXmlRpcServer server_;

    public synchronized SampXmlRpcServer getServer() throws IOException {
        if ( server_ == null ) {
            server_ = new ApacheServer();
        }
        return server_;
    }
}
