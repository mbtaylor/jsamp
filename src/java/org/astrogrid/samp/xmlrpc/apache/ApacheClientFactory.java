package org.astrogrid.samp.xmlrpc.apache;

import java.io.IOException;
import java.net.URL;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;

/**
 * SampXmlRpcClientFactory implementation based on Apache XMLRPC classes.
 *
 * @author   Mark Taylor
 * @since    16 Sep 2008
 */
public class ApacheClientFactory implements SampXmlRpcClientFactory {
    public SampXmlRpcClient createClient( URL endpoint ) throws IOException {
        return new ApacheClient( new XmlRpcClientLite( endpoint ) );
    }
}
