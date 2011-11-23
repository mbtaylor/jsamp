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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.astrogrid.samp.hub.MessageRestriction;
import org.astrogrid.samp.xmlrpc.XmlRpcHubConnection;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

public class WebClientTest extends TestCase {

    public void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( InternalServer.class.getName() )
              .setLevel( Level.SEVERE );
        Logger.getLogger( WebHubConnection.class.getName() )
              .setLevel( Level.SEVERE );
        Logger.getLogger( UrlTracker.class.getName() )
              .setLevel( Level.SEVERE );
        Logger.getLogger( HttpServer.class.getName() )
              .setLevel( Level.SEVERE );
    }

    // Test withdrawn, multiple cycles of allowReverseCallbacks are not
    // recommended.  Code may come in useful for something else another
    // day though.
    public void doNottestCallbacks() throws IOException, InterruptedException {
        WebTestProfile profile =
            new WebTestProfile( new Random( 23L ), true, null );
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

    public void testRegistration() throws IOException {
        WebTestProfile profile =
            new WebTestProfile( new Random( 23L ), true, null );
        profile.startHub();
        assertTrue( profile.isHubRunning() );
        HubConnection conn = profile.register();
        assertEquals( "samp.url-translator", WebClientProfile.URLTRANS_KEY );
        RegInfo regInfo = conn.getRegInfo();
        assertTrue( ((String) regInfo.get( "samp.private-key" )).length() > 0 );
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

    public void testControlledUrlTranslator()
            throws IOException, InterruptedException {
        WebTestProfile profile =
            new WebTestProfile( new Random( 23L ), true, null );
        profile.startHub();
        Subscriptions subs = new Subscriptions();
        String mtype = "test.url";
        subs.addMType( mtype );
        String ftxt = "some\ntext\nin\na\nfile\n";

        HubConnection webConn = profile.register();
        Receiver webReceiver = new Receiver();
        webConn.setCallable( webReceiver );
        webConn.declareSubscriptions( subs );
        RegInfo webReg = webConn.getRegInfo();
        String turl = (String) webReg.get( WebClientProfile.URLTRANS_KEY );

        HubConnection dirConn = profile.registerDirect();
        Receiver dirReceiver = new Receiver();
        dirConn.setCallable( dirReceiver );
        dirConn.declareSubscriptions( subs );
        RegInfo dirReg = dirConn.getRegInfo();

        File file1 = File.createTempFile( "txtfile", ".tmp" );
        file1.deleteOnExit();
        OutputStream fout1 = new FileOutputStream( file1 );
        fout1.write( ftxt.getBytes( "utf-8" ) );
        fout1.close();
        URL furl1 = SampUtils.fileToUrl( file1 );
        URL tfurl1 = new URL( turl + furl1.toString() );
        Message url1Msg = new Message( mtype )
                         .addParam( "url", furl1.toString() );

        // First try - blocked because it's a local URL, and no incoming
        // reference has been seen (UrlTracker).
        try {
            readUrl( tfurl1 );
            fail( "Should have blocked access to " + tfurl1 );
        }
        catch ( IOException e ) {
        }

        // Second try: send message out mentioning it.  This will still
        // fail, because we're not trusted, and it will permanently block
        // access (if we're the first one to mention it, it's probalbly
        // not legitimante and it might provoke an echo somewhere).
        webConn.notify( dirReg.getSelfId(), url1Msg );
        dirReceiver.waitForMessage( 1000 );
        try {
            readUrl( tfurl1 );
            fail( "Should have blocked access to " + tfurl1 );
        }
        catch ( IOException e ) {
        }

        // Third try: get some trusted client to mention the URL,
        // but since it's been permanently blocked, we still can't access it.
        dirConn.notify( webReg.getSelfId(), url1Msg );
        webReceiver.waitForMessage( 1000 );
        try {
            readUrl( tfurl1 );
            fail( "Should have blocked access to " + tfurl1 );
        }
        catch ( IOException e ) {
        }

        // Now try with a different file. 
        File file2 = File.createTempFile( "txtfile", ".tmp" );
        file2.deleteOnExit();
        OutputStream fout2 = new FileOutputStream( file2 );
        fout2.write( ftxt.getBytes( "utf-8" ) );
        fout2.close();
        URL furl2 = SampUtils.fileToUrl( file2 );
        URL tfurl2 = new URL( turl + furl2.toString() );
        Message url2msg = new Message( mtype )
                         .addParam( "url", furl2.toString() );

        // Get a trusted client to mention it.
        dirConn.notify( webReg.getSelfId(), url2msg );
        webReceiver.waitForMessage( 1000 );

        // Read it.  Should be OK.
        assertEquals( ftxt, readUrl( tfurl2 ) );

        // Tidy up.
        file1.delete();
        file2.delete();
        profile.stopHub();
    }

    public void testUncontrolledUrlTranslator() throws IOException {
        WebTestProfile profile =
            new WebTestProfile( new Random( 29L ), false, null );
        profile.startHub();
        HubConnection webConn = profile.register();
        RegInfo webReg = webConn.getRegInfo();
        String turl = (String) webReg.get( WebClientProfile.URLTRANS_KEY );
        File file = File.createTempFile( "txtfile", ".tmp" );
        file.deleteOnExit();
        OutputStream fout = new FileOutputStream( file );
        String ftxt = "some\ntext\nin\na\nfile\n";
        fout.write( ftxt.getBytes( "utf-8" ) );
        fout.close();
        URL furl = SampUtils.fileToUrl( file );
        URL tfurl = new URL( turl + furl.toString() );
        assertEquals( ftxt, readUrl( tfurl ) );
        file.delete();
        profile.stopHub();
    }

    public void testRestrictMTypes() throws IOException, InterruptedException {
        assertTrue( passMessage( ListMessageRestriction.ALLOW_ALL,
                                 "test.hello" ) );
        assertTrue( ! passMessage( ListMessageRestriction.DENY_ALL,
                                   "test.hello" ) );
        assertTrue( passMessage( singleMessageRestriction( true, "test.hello" ),
                                 "test.hello" ) );
        assertTrue( ! passMessage( singleMessageRestriction( true,
                                                             "test.goodbye" ),
                                   "test.hello" ) );
        assertTrue( passMessage( singleMessageRestriction( false,
                                                           "test.goodbye" ),
                                 "test.hello" ) );
        assertTrue( ! passMessage( singleMessageRestriction( false,
                                                             "test.goodbye" ),
                                   "test.goodbye" ) );
        assertTrue( passMessage( singleMessageRestriction( true, "test.*" ),
                                 "test.hello" ) );
        assertTrue( ! passMessage( singleMessageRestriction( true, "test.*" ),
                    "hello" ) );
    }

    private MessageRestriction singleMessageRestriction( final boolean allow,
                                                         String mtype ) {
        return new ListMessageRestriction( allow, new String[] { mtype },
                                           true );
    }

    private boolean passMessage( MessageRestriction mrestrict, String mtype )
            throws IOException, InterruptedException {
        Message msg0 = new Message( mtype );
        WebTestProfile profile =
            new WebTestProfile( new Random( 1019L ), false, mrestrict );
        profile.startHub();
        HubConnection tconn = profile.register();
        HubConnection rconn = profile.register();
        String tid = tconn.getRegInfo().getSelfId();
        String rid = rconn.getRegInfo().getSelfId();
        Subscriptions subs = new Subscriptions();
        subs.addMType( mtype );
        Receiver rcvr = new Receiver();
        rconn.setCallable( rcvr );
        rconn.declareSubscriptions( subs );
        boolean sent;
        try {
            tconn.notify( rid, msg0 );
            sent = true;
        }
        catch ( SampException e ) {
            sent = false;
        }
        if ( sent ) {
            Message msg1 = rcvr.waitForMessage( 1000 );
            assertEquals( msg0, msg1 );
        }
        tconn.unregister();
        rconn.unregister();
        return sent;
    }

    public void testServer() throws IOException {
        ServerSocket sock = new ServerSocket( 0 );
        String path = "/";
        WebHubProfile.ServerFactory sxfact =
            new WebHubProfile.ServerFactory();
        sxfact.setLogType( null );
        sxfact.setPort( 0 );
        sxfact.setXmlrpcPath( "/" );
        sxfact.setOriginAuthorizer( OriginAuthorizers.TRUE );
        sxfact.setAllowFlash( true );
        sxfact.setAllowSilverlight( true );
        InternalServer xServer = sxfact.createSampXmlRpcServer();
        HttpServer hServer = xServer.getHttpServer();
        URL surl =
            new URL( "http://localhost:" + hServer.getSocket().getLocalPort() );
        hServer.start();
        String xdomain = readUrl( new URL( surl, "/crossdomain.xml" ) );
        assertTrue( xdomain.indexOf( "<cross-domain-policy>" ) > 0 );
        String accpol = readUrl( new URL( surl, "/clientaccesspolicy.xml" ) );
        assertTrue( accpol.indexOf( "<access-policy>" ) > 0 );
        hServer.stop();
    }

    public void testMessageRestriction() {
        MessageRestriction allMr = ListMessageRestriction.ALLOW_ALL;
        MessageRestriction noneMr = ListMessageRestriction.DENY_ALL;
        MessageRestriction dfltMr = ListMessageRestriction.DEFAULT;
        MessageRestriction testMr =
            new ListMessageRestriction( true, new String[] { "test.*" }, true );
        MessageRestriction notestMr =
            new ListMessageRestriction( false, new String[] { "test.*" },
                                        true );
        assertTrue( allMr.permitSend( "system.exec", new HashMap() ) );
        assertTrue( ! noneMr.permitSend( "system.exec", new HashMap() ) );
        assertTrue( ! dfltMr.permitSend( "system.exec", new HashMap() ) );
        assertTrue( ! testMr.permitSend( "system.exec", new HashMap() ) );
        assertTrue( notestMr.permitSend( "system.exec", new HashMap() ) );
        assertTrue( dfltMr.permitSend( "table.load.votable", new HashMap() ) );
        assertTrue( new ListMessageRestriction( true,
                                                new String[] { "do.what" },
                                                true )
                   .permitSend( "do.what", new HashMap() ) );
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
