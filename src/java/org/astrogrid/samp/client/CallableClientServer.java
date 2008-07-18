package org.astrogrid.samp.client;

import java.io.IOException;
import java.net.URL;
import org.apache.xmlrpc.WebServer;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;

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
    private final WebServer server_;
    private final ClientXmlRpcHandler clientHandler_;

    private static CallableClientServer instance_;

    /**
     * Constructor.  Note that a singleton-like {@link #getInstance} method
     * exists as well.
     */
    public CallableClientServer() throws SampException, IOException {
        int port = SampUtils.getUnusedPort( 2300 );
        try {
            server_ = new WebServer( port ) {

                // Same as superclass implementation except that the listener
                // thread is marked as a daemon.
                public void start() {
                    if ( this.listener == null ) {
                        this.listener =
                            new Thread( this, "XML-RPC Weblistener" );
                        this.listener.setDaemon( true );
                        this.listener.start();
                    }
                }
            };
            server_.start();
        }
        catch ( Exception e ) {
            throw new SampException( "Can't start XML-RPC server", e );
        }
        clientHandler_ = new ClientXmlRpcHandler();
        server_.addHandler( "samp.client", clientHandler_ );
        url_ =
            new URL( "http://" + SampUtils.getLocalhost() + ":" + port + "/" );
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
     * Returns the XML-RPC WebServer used.
     */
    public WebServer getWebServer() {
        return server_;
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
     * Returns an instance of this class.
     *
     * @return  instance
     */
    public static CallableClientServer getInstance()
            throws SampException, IOException  {
        if ( instance_ == null ) {
            instance_ = new CallableClientServer();
        }
        return instance_;
    }
}
