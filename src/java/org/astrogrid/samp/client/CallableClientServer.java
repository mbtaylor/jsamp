package org.astrogrid.samp.client;

import java.io.IOException;
import java.net.URL;
import org.apache.xmlrpc.WebServer;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;

public class CallableClientServer {

    private final URL url_;
    private final WebServer server_;
    private final ClientXmlRpcHandler clientHandler_;

    private static CallableClientServer instance_;

    public CallableClientServer() throws SampException, IOException {
        int port = SampUtils.getUnusedPort( 2300 );
        try {
            server_ = new WebServer( port );
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

    public URL getUrl() {
        return url_;
    }

    public WebServer getWebServer() {
        return server_;
    }

    public void addClient( String privateKey, CallableClient callable ) {
        clientHandler_.addClient( privateKey, callable );
    }

    public void removeClient( String privateKey ) {
        clientHandler_.removeClient( privateKey );
    }

    public static CallableClientServer getInstance()
            throws SampException, IOException  {
        if ( instance_ == null ) {
            instance_ = new CallableClientServer();
        }
        return instance_;
    }
}
