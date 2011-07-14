package org.astrogrid.samp.web;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.TestProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

public class WebTestProfile extends TestProfile {

    private final int port_;
    private final String path_;
    private final URL hubEndpoint_;
    private final SampXmlRpcClientFactory xClientFactory_;
    private final String baseAppName_;
    private ClientAuthorizer clientAuth_;
    private int regSeq_;

    public WebTestProfile( Random random ) throws IOException {
        this( random, getFreePort(), "/",
              XmlRpcKit.getInstance().getClientFactory(), "test" );
    }

    public WebTestProfile( Random random, int port, String path,
                           SampXmlRpcClientFactory xClientFactory,
                           String baseAppName ) {
        super( random );
        port_ = port;
        path_ = path;
        xClientFactory_ = xClientFactory;
        baseAppName_ = baseAppName;
        clientAuth_ = ClientAuthorizers.createFixedClientAuthorizer( true );
        try {
            hubEndpoint_ = new URL( "http://" + SampUtils.getLocalhost() + ":"
                                  + port + path );
        }
        catch ( MalformedURLException e ) {
            throw new AssertionError();
        }
    }

    public void setClientAuthorizer( ClientAuthorizer clientAuth ) {
        clientAuth_ = clientAuth;
    }

    public HubProfile createHubProfile() throws IOException {
        InternalServer xServer =
            WebHubProfile
           .createSampXmlRpcServer( null, new ServerSocket( port_ ), path_,
                                    OriginAuthorizers.TRUE, false, false );
        ClientAuthorizer copyAuth = new ClientAuthorizer() {
            public boolean authorize( HttpServer.Request request,
                                      String appName ) {
                return clientAuth_.authorize( request, appName );
            }
        };
        return new WebHubProfile( xServer, copyAuth,
                                  new KeyGenerator( "wk:", 24,
                                                    createRandom() ) );
    }

    public boolean isHubRunning() {
        try {
            Socket sock = new Socket( hubEndpoint_.getHost(), port_ );
            sock.close();
            return true;
        }
        catch ( IOException e ) {
            return false;
        }
    }

    public HubConnection register() throws SampException {
        int regSeq;
        synchronized ( this ) {
            regSeq = ++regSeq_;
        }
        Map secMap = new HashMap();
        secMap.put( "samp.name", baseAppName_ + "-" + regSeq );
        try {
            return new WebHubConnection( xClientFactory_
                                        .createClient( hubEndpoint_ ),
                                         secMap );
        }
        catch ( SampException e ) {
            for ( Throwable ex = e; ex != null; ex = ex.getCause() ) {
                if ( ex instanceof ConnectException ) {
                    return null;
                }
            }
            throw e;
        }
        catch ( ConnectException e ) {
            return null;
        }
        catch ( IOException e ) {
            throw new SampException( e );
        }
    }

    private static int getFreePort() throws IOException {
        ServerSocket sock = new ServerSocket( 0 );
        int port = sock.getLocalPort();
        sock.close();
        return port;
    }
}
