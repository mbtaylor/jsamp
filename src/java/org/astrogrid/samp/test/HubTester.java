package org.astrogrid.samp.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.StandardClientProfile;

/**
 * Tester for a running hub.
 * Attempts to test as much of the SAMP standard as possible for an existing
 * hub implementation.
 *
 * @author   Mark Taylor
 * @since    18 Jul 2008
 */
public class HubTester extends Tester {

    private final ClientProfile profile_;
    private final String hubId_;
    private final Client[] ignoreClients_;
    private final Set selfIds_;
    private final Set privateKeys_;
    private final Random random_ = new Random( 233333L );
    private static final String WAITMILLIS_KEY = "test.wait";
    private static final String ECHO_MTYPE = "test.echo";
    private static final String PING_MTYPE = "samp.app.ping";
    private static final String FAIL_MTYPE = "test.fail";
    private static final String ERROR_KEY = "test.error";
    private static Logger logger_ =
        Logger.getLogger( HubTester.class.getName() );

    static {
        org.apache.xmlrpc.XmlRpc.debug = false;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.WARNING );
        Logger.getLogger( "org.astrogrid.samp.SampXmlRpcHandler" )
              .setLevel( Level.SEVERE );
    }

    /**
     * Constructor.
     *
     * @param  profile  hub discovery object
     */
    public HubTester( ClientProfile profile ) throws IOException {
        profile_ = profile;
        selfIds_ = new HashSet();
        privateKeys_ = new HashSet();

        // Set up basic information about the hub for use by other methods.
        // Perform some checks at the same time.
        // First register a probe client to make some basic queries about the
        // current hub state.
        HubConnection conn = profile_.register();
        conn.ping();

        // Acquire, check and store information about the hub.
        RegInfo regInfo = conn.getRegInfo();
        regInfo.check();
        hubId_ = regInfo.getHubId();

        // Keep a record of all clients which have registered on behalf of
        // this class.
        selfIds_.add( regInfo.getSelfId() );
        privateKeys_.add( regInfo.getPrivateKey() );

        // Get list of registered clients.
        String[] clientIds = conn.getRegisteredClients();

        // Check that a client's own ID does not appear in the list.
        assertTrue( ! Arrays.asList( clientIds )
                            .contains( regInfo.getSelfId() ) );

        // Check that the hub's ID does appear in the list.
        assertTrue( Arrays.asList( clientIds ).contains( regInfo.getHubId() ) );

        // Check that the metadata and subscriptions of all the existing 
        // clients is legal.
        int nc = clientIds.length;
        Client[] clients = new Client[ nc ];
        for ( int ic = 0; ic < nc; ic++ ) {
            final String id = clientIds[ ic ];
            final Metadata meta = conn.getMetadata( id );
            meta.check();
            final Subscriptions subs = conn.getSubscriptions( id );
            subs.check();
            clients[ ic ] = new Client() {
                public String getId() {
                    return id;
                }
                public Metadata getMetadata() {
                    return meta;
                }
                public Subscriptions getSubscriptions() {
                    return subs;
                }
            };
        }

        // Store a list of clients which were already registered when this
        // object was created.  May come in useful later.
        ignoreClients_ = clients;

        // Unregister probe client.
        conn.unregister();
    }

    /**
     * Registers with the hub, performing various checks.
     *
     * @return   new hub connection representing a newly-registered client
     */
    private HubConnection register() throws IOException {
        HubConnection conn = profile_.register();
        RegInfo regInfo = conn.getRegInfo();
        regInfo.check();
        String selfId = regInfo.getSelfId();
        String privateKey = regInfo.getPrivateKey();

        // Check that the hub ID is the same for all clients.
        assertEquals( hubId_, regInfo.getHubId() );

        // Check that client IDs are not being reused.
        assertTrue( ! selfIds_.contains( selfId ) );
        selfIds_.add( selfId );

        // Check that private keys are not being reused.
        assertTrue( ! privateKeys_.contains( privateKey ) );
        privateKeys_.add( privateKey );

        // Check that getRegisteredClients() excludes the caller's ID.
        assertTrue( ! Arrays.asList( conn.getRegisteredClients() )
                            .contains( selfId ) );

        // Check that metadata and subscriptions for this application are
        // empty maps (since we have not yet declared their values).
        assertEquals( new HashMap(), conn.getMetadata( selfId ) );
        assertEquals( new HashMap(), conn.getSubscriptions( selfId ) );
        return conn;
    }

    /**
     * Perform a wide variety of tests on a running hub.
     */
    public void run() throws IOException {
        testLockfile();
        testClients();
        calcStorm( 20, 100 );
    }

    /**
     * Tests the content of the SAMP Standard Profile lockfile.
     * Does not currently test the permissions on it - that would be nice
     * but is hard to do from java since it's rather platform-specific.
     */
    private void testLockfile() throws IOException {
        LockInfo lockInfo = LockInfo.readLockFile();
        if ( lockInfo == null ) {
            throw new TestException( "No lockfile (no hub)" );
        }
        lockInfo.check();
    }

    /**
     * Performs a wide variety of tests on a running hub from a limited
     * number of clients.
     */
    private void testClients() throws IOException {

        // Register client 1 with the hub and declare some metadata.
        HubConnection c1 = register();
        String id1 = c1.getRegInfo().getSelfId();
        Metadata meta1 = new Metadata();
        meta1.setName( "Test1" );
        meta1.setDescriptionText( "HubTester client application" );
        meta1.put( "test.drink", "cider" );
        c1.declareMetadata( meta1 );

        // Check the list of clients known to the hub.
        assertTestClients( c1, new String[ 0 ] );

        // Register client 2 with the hub and declare some metadata.
        HubConnection c2 = register();
        String id2 = c2.getRegInfo().getSelfId();
        Metadata meta2 = new Metadata( meta1 );
        meta2.put( "test.drink", "ribena" );
        c2.declareMetadata( meta2 );

        // Check the list of clients known to the hub.
        assertTestClients( c2, new String[] { id1 } );
        assertTestClients( c1, new String[] { id2 } );

        // Check retrieved metadata matches declared metadata.
        assertEquals( meta1, c1.getMetadata( id1 ) );
        assertEquals( meta1, c2.getMetadata( id1 ) );
        assertEquals( meta2, c1.getMetadata( id2 ) );
        assertEquals( meta2, c2.getMetadata( id2 ) );
        assertEquals( "cider", c2.getMetadata( id1 ).get( "test.drink" ) );

        // Redeclare metadata and check that the update has been noted.
        meta1.put( "test.drink", "scrumpy" );
        c1.declareMetadata( meta1 );
        assertEquals( meta1, c1.getMetadata( id1 ) );
        assertEquals( meta1, c2.getMetadata( id1 ) );
        assertEquals( "scrumpy", c2.getMetadata( id1 ).get( "test.drink" ) );

        // Declare subscriptions and check that retrieved subscriptions match
        // declared ones.
        TestCallableClient callable1 = new TestCallableClient( c1 );
        c1.setCallable( callable1 );
        Subscriptions subs1 = new Subscriptions();
        subs1.put( "test.dummy.1", new HashMap() );
        subs1.put( "test.dummy.2", new HashMap() );
        c1.declareSubscriptions( subs1 );
        assertEquals( subs1, c1.getSubscriptions( id1 ) );
        assertEquals( subs1, c2.getSubscriptions( id1 ) );
        Map d3atts = new HashMap();
        d3atts.put( "size", "big" );
        d3atts.put( "colour", "blue" );
        subs1.put( "test.dummy.3", d3atts );
        c1.declareSubscriptions( subs1 );
        assertEquals( subs1, c2.getSubscriptions( id1 ) );
        assertEquals( new HashMap(),
                      ((Map) c2.getSubscriptions( id1 ).get( "test.dummy.1")) );
        assertEquals( "big", 
                      ((Map) c2.getSubscriptions( id1 ).get( "test.dummy.3" ))
                     .get( "size" ) );
        c1.declareSubscriptions( TestCallableClient.SUBS );

        TestCallableClient callable2 = new TestCallableClient( c2 );
        c2.setCallable( callable2 );
        c2.declareSubscriptions( TestCallableClient.SUBS );

        // Try to acquire information about non-existent clients.
        try {
            c1.getMetadata( "Sir Not-Appearing-in-this-Hub" );
            fail();
        }
        catch ( SampException e ) {
        }
        try {
            c1.getSubscriptions( "Sir Not-Appearing-in-this-Hub" );
            fail();
        }
        catch ( SampException e ) {
        }

        // Send some concurrent ECHO messages via both notify and call.
  System.err.println( "Avoiding ugly strings - needs attention in the doc" );
        int necho = 5;
        Object[] echoParams = new Object[ necho ];
        for ( int i = 0; i < necho; i++ ) {
            Message msg = new Message( ECHO_MTYPE );
            Object val1 = createRandomObject( 2, false );
            Object val2 = createRandomObject( 4, false );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 200 + 100 * i ) );
            msg.addParam( "val1", val1 );
            msg.addParam( "val2", val2 );
            echoParams[ i ] = msg.getParams();
            c2.notify( id1, msg );
            callable2.call( id1, "tag" + i, msg );
        }

        // The call messages should complete quickly, so all the sends
        // are expected to complete before any of the replies are received
        // (there is a deliberate delay at the receiver end in all cases).  
        // This isn't required, but hubs SHOULD work this way.
        // Warn if it looks like not.
        if ( callable2.getReplyCount() > necho / 2 ) {
            logger_.warning( "Looks like hub call()/notify() methods "
                           + "not completing quickly"
                           + " (" + callable2.getReplyCount()
                           + "/" + necho + ")" );
        }

        // Spin-wait until all the replies are in.
        while ( callable2.getReplyCount() < necho );
        assertEquals( necho, callable2.getReplyCount() );

        // Check that the replies are as expected (returned samp.result has
        // same content as sent samp.params).
        for ( int i = 0; i < necho; i++ ) {
            assertEquals( necho - i, callable2.getReplyCount() );
            Response r = callable2.getReply( id1, "tag" + i );
            assertEquals( Response.OK_STATUS, r.getStatus() );
            assertEquals( echoParams[ i ], r.getResult() );
        }

        // Check that no more replies have arrived apart from the ones we
        // were expecting.
        assertEquals( 0, callable2.getReplyCount() );

        // Send echo messages synchronously (using callAndWait).
        // These have deliberate delays at the receiver end, but there is
        // no timeout.
        for ( int i = 0; i < necho; i++ ) {
            Message msg = new Message( ECHO_MTYPE );
            Object val1 = createRandomObject( 2, false );
            Object val2 = createRandomObject( 4, false );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 100 * i ) );
            msg.addParam( "val1", val1 );
            msg.addParam( "val2", val2 );
            echoParams[ i ] = msg.getParams();
            Response syncR = c2.callAndWait( id1, msg, 0 );
            assertEquals( Response.OK_STATUS, syncR.getStatus() );
            assertEquals( echoParams[ i ], syncR.getResult() );
        }

        // Send an echo message synchronously, with a timeout which is shorter
        // than the delay which the receiver will introduce.  So the hub
        // SHOULD time this attempt out before the response is received.
        // However, the standard does not REQUIRE this, so just warn in
        // case of no time out.
        {
            Message msg = new Message( ECHO_MTYPE );
            msg.addParam( "text", "copy" );
            int delay = 10000;
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( delay ) );
            long start = System.currentTimeMillis();
            try {
                c2.callAndWait( id1, msg, 1 );
                assert System.currentTimeMillis() - start >= delay;
                logger_.warning( "callAndWait() did not timeout as requested" );
            }
            catch ( SampException e ) {
                // timeout exception
            }
        }

        // Register a new client.
        HubConnection c3 = register();
        TestCallableClient callable3 = new TestCallableClient( c3 );
        c3.setCallable( callable3 );

        // Test callAll and notifyAll.
        {

            // Check that getSubscribedClients returns the right list.
            // Note that it must not include the caller.
            Set recipientSet = c3.getSubscribedClients( ECHO_MTYPE ).keySet();
            assertEquals(
                new HashSet( Arrays.asList( new String[] { id1, id2 } ) ),
                recipientSet );

            // Send an echo message from client 3 using callAll and notifyAll.
            // Should go to clients 1 and 2.
            Message msg = new Message( ECHO_MTYPE );
            Object val4 = createRandomObject( 4, false );
            msg.addParam( "val4", val4 );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 400 ) );
            c3.notifyAll( msg );
            String tag = "tag99";
            callable3.callAll( tag, msg );
            if ( callable3.getReplyCount() != 0 ) {
                logger_.warning( "Looks like hub call()/notify() methods "
                               + "not completing quickly" );
            }

            // Retrieve and check the results from the callAll for each
            // recipient client.
            for ( Iterator it = recipientSet.iterator(); it.hasNext(); ) {
                String rid = (String) it.next();
                Response response = callable3.waitForReply( rid, tag );
                assertEquals( Response.OK_STATUS, response.getStatus() );
                assertEquals( val4, response.getResult().get( "val4" ) );
            }

            // Check there are no replies beyond the ones we expect.
            assertEquals( 0, callable3.getReplyCount() );
            delay( 500 );

            // .. even after a while.
            assertEquals( 0, callable3.getReplyCount() );
        }

        // Test that notify- and call-type messages are being received by
        // their intended recipients.
        {
            Message pingMsg = new Message( PING_MTYPE );
            pingMsg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 100 ) );
            int pingsCount = 50;
            Set recipients = c3.getSubscribedClients( PING_MTYPE ).keySet();
            assertTrue( recipients.contains( id1 ) );
            assertTrue( recipients.contains( id2 ) );
            c3.declareSubscriptions( TestCallableClient.SUBS );
            assertEquals( recipients,
                          c3.getSubscribedClients( PING_MTYPE ).keySet() );

            // Send a load of messages concurrently using various 
            // asynchronous methods.
            for ( int i = 0; i < pingsCount; i++ ) {
                c3.notify( id1, pingMsg );
                callable3.call( id1, "abc1-" + i, pingMsg );
                c3.notifyAll( pingMsg );
                callable3.callAll( "abc2-" + i, pingMsg );
            }

            // Spin-wait until all the clients have received all the messages
            // we have sent.
            int np1 = pingsCount * 4;
            int np2 = pingsCount * 2;
            int np3 = 0;
            int nr3 = pingsCount * ( 1 + recipients.size() );
            while ( callable1.pingCount_ < np1 ||
                    callable2.pingCount_ < np2 ||
                    callable3.pingCount_ < np3 ||
                    callable3.getReplyCount() < nr3 );

            // And then wait a bit to see if any more come in (hopefully not).
            delay( 400 );

            // Check that the number of messages received is exactly as
            // expected.
            assertEquals( np1, callable1.pingCount_ );
            assertEquals( np2, callable2.pingCount_ );
            assertEquals( np3, callable3.pingCount_ );
            assertEquals( nr3, callable3.getReplyCount() );

            // Redeclare client 3's subscriptions to the effect that it
            // will not receive any messages.
            c3.declareSubscriptions( new Subscriptions() );
        }

        // Test that error data encoded in responses works as it should.
        {
            Message failMsg = new Message( FAIL_MTYPE );
            Map error = new HashMap();
            error.put( "samp.errortxt", "failure" );
            error.put( "samp.code", "999" );
            error.put( "do.what", "do.that" );
            failMsg.addParam( ERROR_KEY, error );
            Response reply = c3.callAndWait( id2, failMsg, 0 );
            ErrInfo errInfo = reply.getErrInfo();
            errInfo.check();
            assertEquals( Response.ERROR_STATUS, reply.getStatus() );
            assertEquals( "failure", errInfo.getErrortxt() );
            assertEquals( "999", errInfo.getCode() );
            assertEquals( "do.that", errInfo.get( "do.what" ) );
        }

        // Run tests on MTypes which have not been subscribed to.
        {
            String dummyMtype = "not.an.mtype";
            Message dummyMsg = new Message( dummyMtype );

            // Check the hub does not report clients are subscribed to MTypes
            // they are not subscribed to.
            Set subscribed = c3.getSubscribedClients( dummyMtype ).keySet();
            assertTrue( ! subscribed.contains( id1 ) );
            assertTrue( ! subscribed.contains( id2 ) );

            // Send a message using notify to an unsubscribed recipient.
            // This should result in an error.
            try {
                c3.notify( id1, dummyMsg );
                fail();
            }
            catch ( SampException e ) {
            }

            // Send a message using call to an unsubscribed recipient.
            // This should result in an error.
            try {
                c3.call( id1, "xxx", dummyMsg );
                fail();
            }
            catch ( SampException e ) {
            }


            // Send a message using callAndWait to an unsubscribed recipient.
            // This should result in an error.
            try {
                c3.callAndWait( id1, dummyMsg, 0 );
                fail();
            }
            catch ( SampException e ) {
            }

            // Send message using notifyAll and callAll to which nobody is
            // subscribed.  Nobody will receive this, but it is not an error.
            c3.notifyAll( dummyMsg );
            c3.callAll( "yyy", dummyMsg );
        }

        // Tidy up.
        c3.unregister();
        assertTestClients( c1, new String[] { id2, } );
        assertTestClients( c2, new String[] { id1, } );

        c1.unregister();
        c2.unregister();
    }

    /**
     * Stress-tests the hub by having a lot of clients all sending messages
     * to each other at once.
     *
     * @param   nClient  number of clients
     * @param   nQuery   number of queries per client
     */
    private void calcStorm( final int nClient, final int nQuery )
            throws IOException {

        // Set up clients.
        final Calculator[] calcs = new Calculator[ nClient ];
        final String[] ids = new String[ nClient ];
        for ( int ic = 0; ic < nClient; ic++ ) {
            HubConnection conn = register();
            ids[ ic ] = conn.getRegInfo().getSelfId();
            calcs[ ic ] = new Calculator( conn, random_ );
        }

        // Set up one thread per client to do the message sending.
        Thread[] calcThreads = new Thread[ nClient ];
        final Throwable[] errors = new Throwable[ 1 ];
        for ( int ic = 0; ic < nClient; ic++ ) {
            final Calculator calc = calcs[ ic ];
            calcThreads[ ic ] = new Thread( "Calc" + ic ) {
                public void run() {
                    try {
                        for ( int iq = 0; iq < nQuery && errors[ 0 ] == null;
                              iq++ ) {
                            calc.doSomething( ids[ random_
                                                  .nextInt( nClient ) ] );
                        }
                        calc.flush();
                    }
                    catch ( Throwable e ) {
                        errors[ 0 ] = e;
                    }
                }
            };
        }

        // Start the threads running.
        for ( int ic = 0; ic < nClient; ic++ ) {
            calcThreads[ ic ].start();
        }

        // Wait for all the threads to finish.
        try {
            for ( int ic = 0; ic < nClient; ic++ ) {
                calcThreads[ ic ].join();
            }
        }
        catch ( InterruptedException e ) {
            throw new TestException( "Interrupted", e );
        }

        // Unregister the clients.
        for ( int ic = 0; ic < nClient; ic++ ) {
            calcs[ ic ].getConnection().unregister();
        }

        // If any errors occurred on the sending thread, rethrow one of them 
        // here.
        if ( errors[ 0 ] != null ) {
            throw new TestException( "Error in calculator thread",
                                     errors[ 0 ] );
        }

        // Check that the number of messages sent and the number received
        // was what it should have been.
        int totCalc = 0;
        for ( int ic = 0; ic < nClient; ic++ ) {
            Calculator calc = calcs[ ic ];
            assertEquals( nQuery, calc.getSendCount() );
            totCalc += calc.getReceiveCount();
        }
        assertEquals( totCalc, nClient * nQuery );
    }
 
    /**
     * Assert that the given list of registered clients has a certain content.
     *
     * @param   conn   connection from which to call getRegisteredClients
     * @param  otherIds  array of client public IDs that getRegisteredClients
     *         should return - will not contain ID associated with 
     *         <code>conn</code> itself
     */
    private void assertTestClients( HubConnection conn, String[] otherIds )
            throws IOException {

        // Call getRegisteredClients.
        Set knownOtherIds =
            new HashSet( Arrays.asList( conn.getRegisteredClients() ) );

        // Remove from the list any clients which were already registered
        // before this test instance started up.
        for ( int ic = 0; ic < ignoreClients_.length; ic++ ) {
            String id = ignoreClients_[ ic ].getId();
            knownOtherIds.remove( ignoreClients_[ ic ].getId() );
        }

        // Assert that the (unordered) set retrieved is the same as that
        // asked about.
        assertEquals( knownOtherIds,
                      new HashSet( Arrays.asList( otherIds ) ) );
    }

    /**
     * Generates an object with random content for transmission using SAMP.
     * This may be a structure containing strings, lists and maps with 
     * any legal values as defined by the SAMP data encoding rules.
     *
     * @param   level  maximum level of nesting (how deeply lists/maps 
     *                 may appear within other lists/maps)
     * @param   ugly   if true, any legal SAMP content will be used;
     *                 if false, the returned object should be reasonably
     *                 human-readable if printed (toString)
     * @return  random SAMP object
     */
    public Object createRandomObject( int level, boolean ugly ) {
        if ( level == 0 ) {
            return createRandomString( ugly );
        }
        int type = random_.nextInt( 2 );
        if ( type == 0 ) {
            int nel = random_.nextInt( ugly ? 23 : 3 );
            List list = new ArrayList( nel );
            for ( int i = 0; i < nel; i++ ) {
                list.add( createRandomObject( level - 1, ugly ) );
            }
            SampUtils.checkList( list );
            return list;
        }
        else if ( type == 1 ) {
            int nent = random_.nextInt( ugly ? 23 : 3 );
            Map map = new HashMap( nent );
            for ( int i = 0; i < nent; i++ ) {
                map.put( createRandomString( ugly ),
                         createRandomObject( level - 1, ugly ) );
            }
            SampUtils.checkMap( map );
            return map;
        }
        else {
            throw new AssertionError();
        }
    }

    /**
     * Creates a new random string for transmission using SAMP.
     * This may have any legal content according to the SAMP data encoding
     * rules.
     *
     * @param   ugly   if true, any legal SAMP content will be used;
     *                 if false, the returned object should be reasonably
     *                 human-readable if printed (toString)
     */
    public String createRandomString( boolean ugly ) {
        int nchar = random_.nextInt( ugly ? 99 : 4 );
        char lo = ugly ? 0x01 : 'A';
        char hi = ugly ? 0x7f : 'Z';
        StringBuffer sbuf = new StringBuffer( nchar );
        for ( int i = 0; i < nchar; i++ ) {
            sbuf.append( (char) ( lo + random_.nextInt( hi - lo ) ) );
        }
        String str = sbuf.toString();
        SampUtils.checkString( str );
        return str;
    }

    /**
     * Waits for a given number of milliseconds.
     *
     * @param  millis  number of milliseconds
     */
    private static void delay( int millis ) {
        Object lock = new Object();
        try {
            synchronized ( lock ) {
                lock.wait( millis );
            }
        }
        catch ( InterruptedException e ) {
            throw new RuntimeException( "Interrupted", e );
        }
    }

    /**
     * Main method.  Tests a hub which is currently running.
     */
    public static void main( String[] args ) throws IOException {
        new HubTester( StandardClientProfile.getInstance() ).run();
    }

    /**
     * CallablClient implementation for testing.
     */
    private static class TestCallableClient extends ReplyCollector
                                            implements CallableClient {
        private final HubConnection connection_;
        private int pingCount_;
        public static final Subscriptions SUBS = getSubscriptions();

        /**
         * Constructor.
         *
         * @param  connection   hub connection
         */
        TestCallableClient( HubConnection connection ) {
            super( connection );
            connection_ = connection;
        }

        public void receiveNotification( String senderId, Message msg ) {
            processCall( senderId, msg );
        }

        public void receiveCall( String senderId, String msgId, Message msg ) 
                throws SampException {

            // If the message contains a WAITMILLIS_KEY entry, interpret this
            // as a number of milliseconds to wait before the response is
            // sent back to the hub.
            String swaitMillis = (String) msg.get( WAITMILLIS_KEY );
            if ( swaitMillis != null ) {
                int waitMillis = SampUtils.decodeInt( swaitMillis );
                if ( waitMillis > 0 ) {
                    delay( waitMillis );
                }
            }
            Response response;

            // Process a FAIL_MTYPE message specially.
            if ( msg.getMType().equals( FAIL_MTYPE ) ) {
                Map errs = (Map) msg.getParam( ERROR_KEY );
                if ( errs == null ) {
                    throw new IllegalArgumentException();
                }
                response = Response.createErrorResponse( new ErrInfo( errs ) );
            }

            // For other MTypes, pass them to the processCall method.
            else {
                try {
                    response =
                        Response
                       .createSuccessResponse( processCall( senderId, msg ) );
                }
                catch ( Throwable e ) {
                    response = Response.createErrorResponse( new ErrInfo( e ) );
                }
            }

            // Return the reply, whatever it is, to the hub.
            connection_.reply( msgId, response );
        }

        /**
         * Do the work of responding to a given SAMP message.
         *
         * @param  senderId  sender public ID
         * @param  msg       message object
         * @return  content of the successful reply's samp.result entry
         */
        private Map processCall( String senderId, Message msg ) {
            String mtype = msg.getMType();

            // Returns the samp.params entry as the samp.result entry.
            if ( ECHO_MTYPE.equals( mtype ) ) {
                return msg.getParams();
            }

            // Just bumps a counter and returns an empty samp.result
            else if ( PING_MTYPE.equals( mtype ) ) {
                pingCount_++;
                return new HashMap();
            }

            // Shouldn't happen.
            else {
                throw new TestException( "Unsubscribed MType? " + mtype );
            }
        }

        /**
         * Returns the subscriptions object for this client.
         *
         * @return  subscriptions
         */
        private static Subscriptions getSubscriptions() {
            Subscriptions subs = new Subscriptions();
            subs.addMType( ECHO_MTYPE );
            subs.addMType( PING_MTYPE );
            subs.addMType( FAIL_MTYPE );
            return subs;
        }
    }
}
