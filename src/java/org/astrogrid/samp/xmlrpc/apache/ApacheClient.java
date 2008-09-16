package org.astrogrid.samp.xmlrpc.apache;

import java.io.IOException;
import java.util.List;
import java.util.Vector;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;

/**
 * SampXmlRpcClient implementation based on Apache XMLRPC classes.
 *
 * @author   Mark Taylor
 * @since    16 Sep 2008
 */
public class ApacheClient implements SampXmlRpcClient {

    private final XmlRpcClient xmlrpcClient_;

    /**
     * Constructor.
     *
     * @param  xmlrpcClient  Apache XML-RPC client instance
     */
    public ApacheClient( XmlRpcClient xmlrpcClient ) {
        xmlrpcClient_ = xmlrpcClient;
    }

    public Object callAndWait( String method, List params )
            throws IOException {
        try {
            return xmlrpcClient_
                  .execute( method, (Vector) ApacheUtils.toApache( params ) );
        }
        catch ( XmlRpcException e ) {
            throw (IOException) new IOException( e.getMessage() )
                               .initCause( e );
        }
    }

    public void callAndForget( String method, List params )
            throws IOException {

        // I'm not sure that the Apache implementation is *sufficiently*
        // asynchronous.  It does leave a thread hanging around waiting
        // for a response, though the result of this response is
        // discarded.  May cause problems under heavy load.
        xmlrpcClient_
            .executeAsync( method, (Vector) ApacheUtils.toApache( params ),
                           null );
    }
}
