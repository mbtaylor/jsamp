package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;

/**
 * XML-RPC server which can host {@link CallableClient} instances.
 * There should usually be only one instance of this class for each
 * SampXmlRpcServer - see {@link #getInstance}.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class CallableClientServer {

    private final URL url_;
    private SampXmlRpcServer server_;
    private ClientXmlRpcHandler clientHandler_;

    private static final Map serverMap_ = new HashMap();

    /**
     * Constructor.  Note that a {@link #getInstance} method exists as well.
     *
     * @param  server  XML-RPC server hosting this client server
     */
    public CallableClientServer( SampXmlRpcServer server ) throws IOException {
        server_ = server;
        clientHandler_ = new ClientXmlRpcHandler();
        server_.addHandler( clientHandler_ );
        url_ = server_.getEndpoint();
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
     * @param   connection  hub connection for the registered client on behalf
     *          of which the client will operate
     * @param   callable   callable client object
     */
    public void addClient( HubConnection connection, CallableClient callable ) {
        if ( clientHandler_ == null ) {
            throw new IllegalStateException( "Closed" );
        } 
        clientHandler_.addClient( connection, callable );
    }

    /**
     * Removes a CallableClient object from this server.
     *
     * @param  privateKey   hub connection for which this client was added
     */
    public void removeClient( HubConnection connection ) {
        clientHandler_.removeClient( connection );
    }

    /**
     * Tidies up resources.  Following a call to this method, no further
     * clients can be added.
     */
    public void close() {
        server_.removeHandler( clientHandler_ );
        server_ = null;
        clientHandler_ = null;
    }

    /**
     * Indicates whether this server currently has any clients.
     *
     * @return  true iff there are clients
     */
    boolean hasClients() {
        return clientHandler_ != null && clientHandler_.getClientCount() > 0;
    }

    /**
     * Returns an instance of CallableClientServer for use with a given
     * XML-RPC server.  Because of the implementation, only one 
     * CallableClientServer is permitted per XML-RPC server, so if one 
     * has already been installed for the given <code>server</code>,
     * that one will be returned.  Otherwise a new one will be constructed,
     * installed and returned.
     *
     * <p>To prevent memory leaks, once any clients added to the returned
     * server have been removed (the client count drops to zero), the
     * server will be closed and cannot be re-used.
     *
     * @param  server  XML-RPC server
     * @return   new or re-used CallableClientServer which is installed on
     *           <code>server</code>
     */
    public static synchronized CallableClientServer
                               getInstance( SampXmlRpcServerFactory serverFact )
            throws IOException {
        final SampXmlRpcServer server = serverFact.getServer();
        if ( ! serverMap_.containsKey( server ) ) {
            CallableClientServer clientServer =
                new CallableClientServer( server ) {
                    public void removeClient( HubConnection connection ) {
                        super.removeClient( connection );
                        if ( ! hasClients() ) {
                            close();
                            synchronized ( CallableClientServer.class ) {
                                serverMap_.remove( server );
                            }
                        }
                    }
                };
            serverMap_.put( server, clientServer );
        }
        return (CallableClientServer) serverMap_.get( server );
    }
}
