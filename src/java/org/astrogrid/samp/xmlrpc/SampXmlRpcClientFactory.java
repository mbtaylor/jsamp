package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.URL;

/**
 * Defines a factory which can create clients for communication with 
 * XML-RPC servers.
 *
 * @author   Mark Taylor
 * @since    16 Sep 2008
 */
public interface SampXmlRpcClientFactory {

    /**
     * Returns an XML-RPC client implementation.
     *
     * @param   endpoint  XML-RPC server endpoint
     * @return   client which can communicate with the given endpoint
     */
    SampXmlRpcClient createClient( URL endpoint ) throws IOException;
}
