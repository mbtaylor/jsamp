package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import java.net.URL;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;

/**
 * Freestanding ClientFactory implementation which logs all incoming
 * and outgoing HTTP data.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */ 
public class XmlLoggingInternalClientFactory 
        implements SampXmlRpcClientFactory {
    public SampXmlRpcClient createClient( URL endpoint ) throws IOException {
        return new XmlLoggingInternalClient( endpoint, System.out );
    }
}
