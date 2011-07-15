package org.astrogrid.samp.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.TestProfile;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.xmlrpc.XmlRpcHubConnection;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

public class WebClientTest extends TestCase {

    public void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( InternalServer.class.getName() )
              .setLevel( Level.SEVERE );
    }

    // Test withdrawn, multiple cycles of allowReverseCallbacks are not
    // recommended.  Code may come in useful for something else another
    // day though.
    public void doNottestCallbacks() throws IOException, InterruptedException {
        WebTestProfile profile = new WebTestProfile( new Random( 23L ) );
        profile.startHub();
        XmlRpcHubConnection tconn = (XmlRpcHubConnection) profile.register();
        XmlRpcHubConnection rconn = (XmlRpcHubConnection) profile.register();
        String tid = tconn.getRegInfo().getSelfId();
        String rid = rconn.getRegInfo().getSelfId();
        Subscriptions subs = new Subscriptions();
        String pMtype = "test.ping";
        subs.addMType( pMtype );
        Message pmsg = new Message( pMtype );
        Receiver rcvr = new Receiver();
        rconn.setCallable( rcvr );
        rconn.declareSubscriptions( subs );
        
        tconn.notify( rid, pmsg );
        assertTrue( rcvr.waitForMessage( 1000 ) != null );
        rconn.exec( "allowReverseCallbacks", new String[] { "0" } );
        tconn.notify( rid, pmsg );
        assertTrue( rcvr.waitForMessage( 1000 ) == null );
        rconn.exec( "allowReverseCallbacks", new String[] { "1" } );
        assertTrue( rcvr.waitForMessage( 1000 ) != null );
        assertTrue( rcvr.waitForMessage( 1000 ) == null );
        tconn.unregister();
        rconn.unregister();
    }


    public void testUrlTranslator() throws IOException {
        WebTestProfile profile = new WebTestProfile( new Random( 23L ) );
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

        profile.setClientAuthorizer( ClientAuthorizers
                                    .createFixedClientAuthorizer( false ) );
        try {
            profile.register();
            fail();
        }
        catch ( SampException e ) {
            assertTrue( e.getMessage().indexOf( "denied" ) > 0 );
        }
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

    private static class Receiver implements CallableClient {
        private final List msgList_ = new ArrayList();
        private final List responseList_ = new ArrayList();
        public synchronized void receiveCall( String senderId, String msgId,
                                              Message msg ) {
            msgList_.add( msg );
            notifyAll();
        }
        public synchronized void receiveNotification( String senderId,
                                                      Message msg ) {
            msgList_.add( msg );
            notifyAll();
        }
        public synchronized void receiveResponse( String responsderId,
                                                  String msgTag,
                                                  Response response ) {
            msgList_.add( response );
            notifyAll();
        }
        public synchronized Message waitForMessage( long millis )
                throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            while ( System.currentTimeMillis() < end && msgList_.isEmpty() ) {
                wait( end - System.currentTimeMillis() );
            }
            return msgList_.isEmpty() ? null
                                      : (Message) msgList_.get( 0 );
        }
        public synchronized Response waitForResponse( long millis )
                throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            while ( System.currentTimeMillis() < end &&
                    responseList_.isEmpty() ) {
                wait( end - System.currentTimeMillis() );
            }
            return responseList_.isEmpty() ? null
                                           : (Response) responseList_.get( 0 );
        }
    }
}
