package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import org.astrogrid.samp.client.CallableClient;

/**
 * XML-RPC server which can host {@link CallableClient} instances.
 * It is usually appropriate to treat this class as a singleton, since
 * in most cases a single instance will be more efficient than multiple ones,
 * but you can construct your own instances if you want.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class CallableClientServer {

    private final URL url_;
    private final ClientXmlRpcHandler clientHandler_;

    private static final Map serverMap_ = new HashMap();

    /**
     * Constructor.  Note that a singleton-like {@link #getInstance} method
     * exists as well.
     *
     * @param  server  XML-RPC server hosting this client server
     */
    public CallableClientServer( SampXmlRpcServer server ) throws IOException {
        clientHandler_ = new ClientXmlRpcHandler();
        server.addHandler( clientHandler_ );
        url_ = server.getEndpoint();
    }

    /**
     * Returns the XML-RPC endpoint for this server.
     *
     * @return  endpoint url
     */
    public URL getUrl() {
        return url_;
    }

    /**
     * Adds a CallableClient object to this server.
     *
     * @param   privateKey  private key for the registered client on behalf
     *          of which the client will operate
     * @param   callable   callable client object
     */
    public void addClient( String privateKey, CallableClient callable ) {
        clientHandler_.addClient( privateKey, callable );
    }

    /**
     * Removes a CallableClient object from this server.
     *
     * @param  privateKey   key under which this client was added
     */
    public void removeClient( String privateKey ) {
        clientHandler_.removeClient( privateKey );
    }

    /**
     * Returns an instance of CallableClientServer for use with a given
     * XML-RPC server.  Because of the implementation, only one 
     * CallableClientServer is permitted per XML-RPC server, so if one 
     * has already been installed for the given <code>server</code>,
     * that one will be returned.  Otherwise a new one will be constructed,
     * installed and returned.
     *
     * @param  server  XML-RPC server
     * @return   new or re-used CallableClientServer which is installed on
     *           <code>server</code>
     */
    public static synchronized CallableClientServer
                               getInstance( SampXmlRpcServer server )
            throws IOException {
        if ( ! serverMap_.containsKey( server ) ) {
            serverMap_.put( server, new CallableClientServer( server ) );
        }
        return (CallableClientServer) serverMap_.get( server );
    }
}
