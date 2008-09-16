package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import java.net.URL;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;

/**
 * Freestanding SampXmlRpcClientFactory implementation.
 * This implementation requires no external libraries.
 *
 * @author   Mark Taylor
 * @since    16 Sep 2008
 */
public class InternalClientFactory implements SampXmlRpcClientFactory {
    public SampXmlRpcClient createClient( URL endpoint ) throws IOException {
        return new InternalClient( endpoint );
    }
}
