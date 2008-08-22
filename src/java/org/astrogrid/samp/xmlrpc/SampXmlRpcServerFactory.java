package org.astrogrid.samp.xmlrpc;

import java.io.IOException;

/**
 * Defines a factory for SampXmlRpcServer instances.
 * In most cases it will make sense to implement this class so that 
 * a single server instance is constructed lazily, and the same instance
 * is always returned from the {@link #getServer} method.
 * This means that the same server can be used for everything that requires
 * an XML-RPC server, thus keeping resource usage down.
 * Users of this class must keep this implementation model in mind,
 * so must not assume that a new instance is returned each time.
 * But if an implementation wants to return a new instance each time for
 * some reason, that is permissible.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public interface SampXmlRpcServerFactory {

    /**
     * Returns an XML-RPC server implementation.
     * Implementations are permitted, but not required, to return the same
     * object from different calls of this method.
     *
     * @return  new or re-used server
     */
    SampXmlRpcServer getServer() throws IOException;
}
