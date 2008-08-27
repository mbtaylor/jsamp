package org.astrogrid.samp;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.xmlrpc.XmlRpcImplementation;

public class HubConnectorTest extends TestCase {

    private final Random random_ = new Random( 2323L );
    private final TestClientProfile[] profiles_ = 
        TestClientProfile.getTestProfiles( random_ );
    private static final String ECHO_MTYPE = "test.echo";

    protected void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
    }

    public void testConnector() throws IOException {
        for ( int i = 0; i < profiles_.length; i++ ) {
            testConnector( profiles_[ i ] );
        }
    }

    private void testConnector( TestClientProfile profile ) throws IOException {
        assertNull( profile.register() );
        HubConnector connector = new HubConnector( profile );
        connector.setAutoconnect( 1 );
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
        c0.unregister();
        delay( 1500 );
        assertTrue( connector.isConnected() );
        assertNotNull( connector.getConnection() );
        assertEquals( 2, clientMap.size() );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getHubId() ) );
        assertTrue( clientMap.containsKey( connector.getConnection()
                                          .getRegInfo().getSelfId() ) );

        assertEquals( meta, connector.getMetadata() );
        assertEquals( meta, getMetadata( connector.getConnection() ) );

        assertEquals( null, connector.getSubscriptions() );
        assertEquals( new HashMap(),
                      getSubscriptions( connector.getConnection() ) );
        Subscriptions subs = connector.computeSubscriptions();
        assertTrue( subs.containsKey( "samp.hub.event.register" ) );
        assertTrue( ! subs.containsKey( ECHO_MTYPE ) );
        connector.declareSubscriptions( subs );
        assertEquals( subs, connector.getSubscriptions() );
        assertEquals( subs, getSubscriptions( connector.getConnection() ) );
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
        assertEquals( subs,
                      ((Client) clientMap.get( connector.getConnection()
                                              .getRegInfo().getSelfId() ))
                     .getSubscriptions() );
        meta.put( "colour", "blue" );
        connector.declareMetadata( meta );
        delay( 500 );
        assertEquals( meta, connector.getMetadata() );
        assertEquals( meta, getMetadata( connector.getConnection() ) );
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
        for ( int i = 0; i < profiles_.length; i++ ) {
            testSynch( profiles_[ i ] );
        }
    }

    private void testSynch( TestClientProfile profile ) throws IOException {
        profile.startHub();
        TestMessageHandler echo = new TestMessageHandler();

        HubConnector c1 = new HubConnector( profile );
        c1.addMessageHandler( echo );
        Subscriptions subs1 = new Subscriptions();
        subs1.addMType( ECHO_MTYPE );
        c1.declareSubscriptions( subs1 );

        HubConnector c2 = new HubConnector( profile );
        c2.addMessageHandler( echo );
        c2.declareSubscriptions( c2.computeSubscriptions() );

        Map params = new HashMap();
        params.put( "names", Arrays.asList( new String[] { "Arthur", "Gordon",
                                                           "Pym" } ) );
        Message msg = new Message( ECHO_MTYPE, params );
        msg.check();
        Response r1 =
            c1.callAndWait( c2.getConnection().getRegInfo().getSelfId(),
                            msg, 0 );
        Response r2 =
            c2.callAndWait( c1.getConnection().getRegInfo().getSelfId(),
                            msg, 0 );
        assertEquals( Response.OK_STATUS, r1.getStatus() );
        assertEquals( Response.OK_STATUS, r2.getStatus() );
        assertEquals( params, r1.getResult() );
        assertEquals( params, r2.getResult() );

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

    private void delay( int millis ) {
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
            return msg.getParams();
        }
    }
}
