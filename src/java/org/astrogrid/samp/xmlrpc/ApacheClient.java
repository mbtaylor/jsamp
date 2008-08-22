package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;

/**
 * SampXmlRpcClient implementation based on Apache XMLRPC classes.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public class ApacheClient implements SampXmlRpcClient {
    private final Map clientMap_;

    /**
     * Constructor.
     */
    public ApacheClient() {
        clientMap_ = new HashMap();
    }

    public Object callAndWait( String endpoint, String method, List params )
            throws IOException {
        try {
            return getApacheClient( endpoint )
                  .execute( method, (Vector) ApacheUtils.toApache( params ) );
        }
        catch ( XmlRpcException e ) {
            throw (IOException) new IOException( e.getMessage() )
                               .initCause( e );
        }
    }

    public void callAndForget( String endpoint, String method, List params )
            throws IOException {

        // I'm not sure that the Apache implementation is *sufficiently*
        // asynchronous.  It does leave a thread hanging around waiting
        // for a response, though the result of this response is
        // discarded.  May cause problems under heavy load.
        getApacheClient( endpoint )
            .executeAsync( method, (Vector) ApacheUtils.toApache( params ),
                           null );
    }

    /**
     * Returns a client for talking to a given endpoint.
     *
     * @param  endpoint  server endpoint
     * @return   new or re-used client
     */
    private XmlRpcClient getApacheClient( String endpoint )
            throws MalformedURLException {
        if ( ! clientMap_.containsKey( endpoint ) ) {
            clientMap_.put( endpoint, createApacheClient( endpoint ) );
        }
        return (XmlRpcClient) clientMap_.get( endpoint );
    }

    /**
     * Constructs a new Apache client.
     * The default implementation returns an XmlRpcClientLite.
     *
     * @param  endpoint
     * @return  new client
     */
    protected XmlRpcClient createApacheClient( String endpoint )
            throws MalformedURLException {
        return new XmlRpcClientLite( new URL( endpoint ) );
    }
}
