package org.astrogrid.samp.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.TestProfile;
import org.astrogrid.samp.xmlrpc.internal.InternalServer;

public class HubConnectorTest extends TestCase {

    private final Random random_ = new Random( 2323L );
    private static final String ECHO_MTYPE = "test.echo";

    protected void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.SEVERE );
    }

    public void testConnector() throws IOException, InterruptedException {
        TestProfile[] profiles = TestProfile.createTestProfiles( random_ );
        for ( int i = 0; i < profiles.length; i++ ) {
            testConnector( profiles[ i ] );
        }
    }

    private void testConnector( TestProfile profile )
            throws IOException, InterruptedException {
        assertNull( profile.register() );
        HubConnector connector = new HubConnector( profile );
        connector.setAutoconnect( 1 );
        connector.declareSubscriptions( connector.computeSubscriptions() );

        Map clientMap = connector.getClientMap();
        assertTrue( clientMap.isEmpty() );

        Metadata meta = new Metadata();
        meta.setName( "HubConnectorTest" );
        meta.put( "colour", "yellow" );
        connector.declareMetadata( meta );
        assertEquals( meta, connector.getMetadata() );

        assertTrue( ! connector.isConnected() );
        profile.startHub();
        HubConnection c0 = profile.register();
        assertNotNull( c0 );
        assertEquals( new HashMap(), getSubscriptions( c0 ) );

        // Sometimes these tests just don't work.  It's because of the
        // indeterminate nature of SAMP - message delivery order is not
        // guaranteed etc.  I could remove these tests but .. most of the
        // time they should, and do, pass.
        synchronized ( clientMap ) {
            while ( clientMap.size() != 3 ) clientMap.wait();
        }
        c0.unregister();
        synchronized ( clientMap ) {
            while ( clientMap.size() != 2 ) clientMap.wait();
        }
        assertTrue( connector.isConnected() );
        assertNotNull( connector.getConnection() );
        assertEquals( 2, clientMap.size() );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getHubId() ) );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getSelfId() ) );

        assertEquals( meta, connector.getMetadata() );
        assertEquals( meta, getMetadata( connector.getConnection() ) );
        String selfId = connector.getConnection().getRegInfo().getSelfId();
        synchronized ( clientMap ) {
            while ( ! meta.equals( ((Client) clientMap.get( selfId ))
                                  .getMetadata() ) ) {
                clientMap.wait();
            }
        }

        Subscriptions subs = connector.getSubscriptions();
        assertTrue( subs.containsKey( "samp.hub.event.register" ) );
        assertTrue( ! subs.containsKey( ECHO_MTYPE ) );
        assertEquals( subs, getSubscriptions( connector.getConnection() ) );
        synchronized ( clientMap ) {
            while ( ! subs.equals( ((Client) clientMap.get( selfId ))
                                  .getSubscriptions() ) ) {
                clientMap.wait();
            }
        }

        connector.addMessageHandler( new TestMessageHandler() );
        subs = connector.computeSubscriptions();
        assertTrue( subs.containsKey( ECHO_MTYPE ) );
        assertTrue( ! subs.equals( connector.getSubscriptions() ) );
        assertTrue( ! subs
                     .equals( getSubscriptions( connector.getConnection() ) ) );
        connector.declareSubscriptions( subs );
        assertEquals( subs, connector.getSubscriptions() );
        assertEquals( subs, getSubscriptions( connector.getConnection() ) );

        assertEquals( meta,
                      ((Client) clientMap.get( connector.getConnection()
                                              .getRegInfo().getSelfId() ))
                     .getMetadata() );
        meta.put( "colour", "blue" );
        connector.declareMetadata( meta );
        assertEquals( meta, connector.getMetadata() );
        assertEquals( meta, getMetadata( connector.getConnection() ) );

        delay( 500 );
        assertEquals( subs,
                      ((Client) clientMap.get( connector.getConnection()
                                              .getRegInfo().getSelfId() ))
                     .getSubscriptions() );
        assertEquals( meta,
                      ((Client) clientMap.get( connector.getConnection()
                                              .getRegInfo().getSelfId() ))
                     .getMetadata() );

        RegInfo regInfo0 = connector.getConnection().getRegInfo();
        connector.setActive( false );
        assertNull( connector.getConnection() );
        assertTrue( clientMap.isEmpty() );
        connector.setActive( true );
        RegInfo regInfo1 = connector.getConnection().getRegInfo();
        assertTrue( ! regInfo0.getSelfId().equals( regInfo1.getSelfId() ) );
        assertEquals( regInfo0.getHubId(), regInfo1.getHubId() );
        connector.setAutoconnect( 0 );
        profile.stopHub();
        delay( 500 );
        assertNull( connector.getConnection() );
        assertTrue( clientMap.isEmpty() );
        profile.startHub();
        RegInfo regInfo2 = connector.getConnection().getRegInfo();
        assertTrue( ! regInfo0.getPrivateKey()
                     .equals( regInfo2.getPrivateKey() ) );
        assertTrue( ! regInfo1.getPrivateKey()
                     .equals( regInfo2.getPrivateKey() ) );

        assertEquals( 2, clientMap.size() );
        assertEquals( meta, getMetadata( connector.getConnection() ) );
        assertEquals( subs, getSubscriptions( connector.getConnection() ) );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getHubId() ) );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getSelfId() ) );
        connector.getConnection().unregister();
        profile.stopHub();
    }

    public void testSynch() throws IOException {
        TestProfile[] profiles = TestProfile.createTestProfiles( random_ );
        for ( int i = 0; i < profiles.length; i++ ) {
            testSynch( profiles[ i ] );
        }
    }

    private void testSynch( TestProfile profile ) throws IOException {
        profile.startHub();
        TestMessageHandler echo = new TestMessageHandler();

        HubConnector c1 = new HubConnector( profile );
        String id1 = c1.getConnection().getRegInfo().getSelfId();
        c1.addMessageHandler( echo );
        Subscriptions subs1 = new Subscriptions();
        subs1.addMType( ECHO_MTYPE );
        c1.declareSubscriptions( subs1 );

        HubConnector c2 = new HubConnector( profile );
        String id2 = c2.getConnection().getRegInfo().getSelfId();
        c2.addMessageHandler( echo );
        c2.declareSubscriptions( c2.computeSubscriptions() );

        Map params = new HashMap();
        params.put( "names", Arrays.asList( new String[] { "Arthur", "Gordon",
                                                           "Pym" } ) );
        Message msg = new Message( ECHO_MTYPE, params );
        msg.check();
        Response r1 =
            c1.callAndWait( id2, msg, 0 );
        Response r2 =
            c2.callAndWait( id1, msg, 0 );
        assertEquals( Response.OK_STATUS, r1.getStatus() );
        assertEquals( Response.OK_STATUS, r2.getStatus() );
        assertEquals( params, r1.getResult() );
        assertEquals( params, r2.getResult() );

        TestResultHandler tHandler = new TestResultHandler();
        c1.call( id2, msg, tHandler, 0 );
        int millis = tHandler.waitTillDone();
        assert tHandler.getResponse( id2 ).isOK();

        msg.addParam( "waitMillis", "5000" );
        TestResultHandler th2 = new TestResultHandler();
        TestResultHandler th3 = new TestResultHandler();
        c1.call( id2, msg, th2, 1 );
        c1.callAll( msg, th3, 1 );
        assertTrue( ! th2.isDone_ );
        assertTrue( ! th3.isDone_ );
        int delay = th2.waitTillDone() + th3.waitTillDone();
        assertTrue( delay > 400 );
        assertTrue( th2.isDone_ );
        assertTrue( th3.isDone_ );
        assertTrue( th2.getResponse( id2 ) == null );
        assertTrue( th3.getResponse( id2 ) == null );

        profile.stopHub();
    }

    private Subscriptions getSubscriptions( HubConnection connection )
            throws SampException {
        return connection
              .getSubscriptions( connection.getRegInfo().getSelfId() );
    }

    private Metadata getMetadata( HubConnection connection )
            throws SampException {
        return connection.getMetadata( connection.getRegInfo().getSelfId() );
    }

    static void delay( int millis ) {
        Object o = new Object();
        synchronized ( o ) {
            try {
                o.wait( millis );
            }
            catch ( InterruptedException e ) {
                throw new RuntimeException( e );
            }
        }
    }

    private static class TestMessageHandler extends AbstractMessageHandler {
        TestMessageHandler() {
            super( ECHO_MTYPE );
        }
        public Map processCall( HubConnection conn, String senderId,
                                Message msg ) {
            String waitParam = (String) msg.getParam( "waitMillis" );
            if ( waitParam != null ) {
                int delay = SampUtils.decodeInt( waitParam );
                delay( delay );
            }
            return msg.getParams();
        }
    }

    private static class TestResultHandler implements ResultHandler {
        boolean isDone_;
        final Map resultMap_ = Collections.synchronizedMap( new HashMap() );

        public synchronized void result( Client client, Response response ) {
            resultMap_.put( client.getId(), response );
            notifyAll();
        }

        public synchronized void done() {
            isDone_ = true;
            notifyAll();
        }

        public Response getResponse( String clientId ) {
            return (Response) resultMap_.get( clientId );
        }

        public synchronized int waitTillDone() {
            long start = System.currentTimeMillis();
            while ( ! isDone_ ) {
                try {
                    wait();
                }
                catch ( InterruptedException e ) {
                    fail();
                }
            }
            return (int) ( System.currentTimeMillis() - start );
        }

        public void reset() {
            isDone_ = false;
            resultMap_.clear();
        }
    }

    public static void main( String[] args ) throws Exception {
        HubConnectorTest t = new HubConnectorTest();
        t.setUp();
        t.testConnector();
    }
}
