package org.astrogrid.samp.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.TestProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

public class WebClientTest extends TestCase {

    public void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
    }

    public void testUrlTranslator() throws IOException {
        TestProfile profile = new WebTestProfile( new Random( 23L ) );
        profile.startHub();
        assertTrue( profile.isHubRunning() );
        HubConnection conn = profile.register();
        assertEquals( "samp.url-translator", WebClientProfile.URLTRANS_KEY );
        RegInfo regInfo = conn.getRegInfo();
        assertTrue( ((String) regInfo.get( "samp.private-key" )).length() > 0 );
        String turl = (String) regInfo.get( WebClientProfile.URLTRANS_KEY );
        File file = File.createTempFile( "txtfile", ".tmp" );
        file.deleteOnExit();
        OutputStream fout = new FileOutputStream( file );
        String ftxt = "some\ntext\nin\na\nfile\n";
        fout.write( ftxt.getBytes( "utf-8" ) );
        fout.close();
        URL furl = new URL( turl + SampUtils.fileToUrl( file ).toString() );
        assertEquals( ftxt, readUrl( furl ) );
        file.delete();
        profile.stopHub();
    }

    public void testServer() throws IOException {
        ServerSocket sock = new ServerSocket( 0 );
        String path = "/";
        InternalServer xServer =
            WebHubProfile.createSampXmlRpcServer( null, sock, "/",
                                                  OriginAuthorizers.TRUE,
                                                  true, true );
        URL surl = new URL( "http://localhost:" + sock.getLocalPort() );
        HttpServer hServer = xServer.getHttpServer();
        hServer.start();
        String xdomain = readUrl( new URL( surl, "/crossdomain.xml" ) );
        assertTrue( xdomain.indexOf( "<cross-domain-policy>" ) > 0 );
        String accpol = readUrl( new URL( surl, "/clientaccesspolicy.xml" ) );
        assertTrue( accpol.indexOf( "<access-policy>" ) > 0 );
        hServer.stop();
    }

    private static String readUrl( URL url ) throws IOException {
        StringBuffer ubuf = new StringBuffer();
        InputStream in = url.openStream();
        for ( int b; ( b = in.read() ) >= 0; ) {
            ubuf.append( (char) b );
        }
        in.close();
        return ubuf.toString();
    }
}
