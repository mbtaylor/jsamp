package org.astrogrid.samp.test;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.gui.HubMonitor;
import org.astrogrid.samp.xmlrpc.LockInfo;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;

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
    private final ClientWatcher clientWatcher_;
    private final Random random_ = new Random( 233333L );
    private static final String WAITMILLIS_KEY = "test.wait";
    private static final String MSGIDQUERY_KEY = "test.msgid";
    private static final String ECHO_MTYPE = "test.echo";
    private static final String PING_MTYPE = "samp.app.ping";
    private static final String FAIL_MTYPE = "test.fail";
    private static final String REGISTER_MTYPE = "samp.hub.event.register";
    private static final String UNREGISTER_MTYPE = "samp.hub.event.unregister";
    private static final String METADATA_MTYPE = "samp.hub.event.metadata";
    private static final String SUBSCRIPTIONS_MTYPE =
        "samp.hub.event.subscriptions";
    private static final String ERROR_KEY = "test.error";
    private static final char[] ALPHA_CHARS = createAlphaCharacters();
    private static final char[] GENERAL_CHARS = createGeneralCharacters();
 
    private static Logger logger_ =
        Logger.getLogger( HubTester.class.getName() );

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
        if ( conn == null ) {
            throw new IOException( "No hub is running" );
        }
        conn.ping();

        // Set up monitor to receive hub event messages.
        clientWatcher_ = new ClientWatcher( conn );
        conn.setCallable( clientWatcher_ );
        conn.declareSubscriptions( clientWatcher_.getSubscriptions() );
        conn.declareMetadata( clientWatcher_.getMetadata() );

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

        // Check that the hub's ID does appear in the list.
        assertTrue( Arrays.asList( clientIds ).contains( regInfo.getHubId() ) );

        // Check that a client's own ID does not appear in the list.
        assertTrue( ! Arrays.asList( clientIds )
                            .contains( regInfo.getSelfId() ) );

        // But prepare a list which contains the client's ID as well.
        String[] clientIds1 = new String[ clientIds.length + 1 ];
        System.arraycopy( clientIds, 0, clientIds1, 0, clientIds.length );
        clientIds1[ clientIds.length ] = regInfo.getSelfId();
        clientIds = clientIds1;

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
        ignoreClients_ = clients;
    }

    /**
     * Registers with the hub, performing various checks.
     *
     * @return   new hub connection representing a newly-registered client
     */
    private HubConnection register() throws SampException {
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

        // Profile-specific tests.
        if ( profile_ == StandardClientProfile.getInstance() ) {
            testStandardLockfile();
        }
        if ( profile_ instanceof StandardClientProfile ) {
            testLockInfo( ((StandardClientProfile) profile_).getLockInfo() );
        }

        // General tests.
        testClients();
        testStress();
    }

    /**
     * Tests the content of the SAMP Standard Profile lockfile.
     * Does not currently test the permissions on it - that would be nice
     * but is hard to do from java since it's rather platform-specific.
     */
    private void testStandardLockfile() throws IOException {
        LockInfo lockInfo = LockInfo.readLockFile();
        if ( lockInfo == null ) {
            throw new TestException( "No lockfile (no hub)" );
        }
    }

    /**
     * Does tests on a LockInfo object used by the profile.
     * This is specific to a Standard-like (though not necessarily Standard)
     * profile, and will simply be skipped for non-standard profiles.
     *
     * @param   lockInfo   lock info object describing a running hub
     */
    private void testLockInfo( LockInfo lockInfo ) throws IOException {
        if ( lockInfo == null ) {
            throw new TestException( "No LockInfo (no hub)" );
        }
        lockInfo.check();
        String secret = lockInfo.getSecret();
        URL hubUrl = lockInfo.getXmlrpcUrl();
        TestXmlrpcClient xclient = new TestXmlrpcClient( hubUrl );
        xclient.checkSuccessCall( "samp.hub.ping", new ArrayList() );
        xclient.checkFailureCall( "samp.hub.register",
                                  Collections.singletonList( secret
                                                           + "-NOT!" ) );
        xclient.checkFailureCall( "samp.hub.not-a-method",
                                  Collections.singletonList( secret ) );
    }

    /**
     * Performs a wide variety of tests on a running hub from a limited
     * number of clients.
     */
    private void testClients() throws IOException {

        // Register client0, set metadata and subscriptions, and unregister.
        HubConnection c0 = register();
        String id0 = c0.getRegInfo().getSelfId();
        Metadata meta0 = new Metadata();
        meta0.setName( "Shorty" );
        meta0.setDescriptionText( "Short-lived test client" );
        c0.declareMetadata( meta0 );
        TestCallableClient callable0 = new TestCallableClient( c0 );
        c0.setCallable( callable0 );
        Subscriptions subs0 = new Subscriptions();
        subs0.put( ECHO_MTYPE, new HashMap() );
        subs0.check();
        c0.declareSubscriptions( subs0 );
        c0.unregister();

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
        int necho = 5;
        Map[] echoParams = new Map[ necho ];
        String[] msgIds = new String[ necho ];
        for ( int i = 0; i < necho; i++ ) {
            Message msg = new Message( ECHO_MTYPE );
            Object val1 = createRandomObject( 2, false );
            Object val2 = createRandomObject( 4, false );
            Object val3 = new String( GENERAL_CHARS );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 200 + 100 * i ) );
            msg.put( MSGIDQUERY_KEY, SampUtils.encodeBoolean( true ) );
            msg.addParam( "val1", val1 );
            msg.addParam( "val2", val2 );
            msg.addParam( "val3", val3 );
            echoParams[ i ] = msg.getParams();
            c2.notify( id1, msg );
            msgIds[ i ] = callable2.call( id1, "tag" + i, msg );
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
        while ( callable2.getReplyCount() < necho ) delay( 100 );
        assertEquals( necho, callable2.getReplyCount() );

        // Check that the replies are as expected (returned samp.result has
        // same content as sent samp.params).
        for ( int i = 0; i < necho; i++ ) {
            assertEquals( necho - i, callable2.getReplyCount() );
            Response r = callable2.getReply( id1, "tag" + i );
            assertEquals( Response.OK_STATUS, r.getStatus() );
            assertEquals( echoParams[ i ], r.getResult() );
            assertEquals( msgIds[ i ], r.get( MSGIDQUERY_KEY ) );
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
            Object val3 = new String( GENERAL_CHARS );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 100 * i ) );
            msg.addParam( "val1", val1 );
            msg.addParam( "val2", val2 );
            msg.addParam( "val3", val3 );
            echoParams[ i ] = msg.getParams();
            Response syncR = c2.callAndWait( id1, msg, 0 );
            assertEquals( Response.OK_STATUS, syncR.getStatus() );
            assertEquals( echoParams[ i ], syncR.getResult() );
        }

        // Send some longer messages synchronously.
        {
            for ( int ie = 0; ie < 5; ie++ ) {
                int num = (int) Math.pow( 10, ie );
                List list = new ArrayList( num );
                for ( int in = 0; in < num; in++ ) {
                    list.add( SampUtils.encodeInt( in + 1 ) );
                }
                Message msg = new Message( ECHO_MTYPE );
                msg.addParam( "list", list );
                msg.check();
                Response response = c2.callAndWait( id1, msg, 0 );
                response.check();
                assertEquals( Response.OK_STATUS, response.getStatus() );
                assertEquals( list, response.getResult().get( "list" ) );
            }
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
            try {
                assertEquals(
                    new HashSet( Arrays.asList( new String[] { id1, id2 } ) ),
                    recipientSet );
            }
            catch ( TestException e ) {
                throw new TestException( "You may need to shut down other "
                                       + "SAMP clients first", e ); 
            }

            // Send an echo message from client 3 using callAll and notifyAll.
            // Should go to clients 1 and 2.
            Message msg = new Message( ECHO_MTYPE );
            Object val4 = createRandomObject( 4, false );
            msg.addParam( "val4", val4 );
            msg.put( WAITMILLIS_KEY, SampUtils.encodeInt( 400 ) );
            List notifyList = c3.notifyAll( msg );
            assertEquals( recipientSet, new HashSet( notifyList ) );
            String tag = "tag99";
            msg.put( MSGIDQUERY_KEY, "1" );
            Map callMap = callable3.callAll( tag, msg );
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
                assertEquals( (String) callMap.get( rid ),
                              response.get( MSGIDQUERY_KEY ) );
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
                List notifyList = c3.notifyAll( pingMsg );
                Map callMap = callable3.callAll( "abc2-" + i, pingMsg );
                assertEquals( recipients, new HashSet( notifyList ) );
                assertEquals( recipients, callMap.keySet() );
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
                    callable3.getReplyCount() < nr3 ) delay( 100 );

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
            List notifyList = c3.notifyAll( dummyMsg );
            assertEquals( 0, notifyList.size() );
            Map callMap = c3.callAll( "yyy", dummyMsg );
            assertEquals( 0, callMap.size() );
        }

        // Check that hub event messages arrived concerning client 0 which
        // we registered and unregistered earlier.  Do it here to give 
        // messages enough time to have arrived; SAMP offers no guarantees
        // of delivery sequence, but if they haven't showed up yet it's 
        // very likely that they never will.
        Throwable cwError = clientWatcher_.getError();
        if ( cwError != null ) {
            throw new TestException( "Error encountered during hub event " 
                                   + "processing", cwError );
        }
        WatchedClient client0 = clientWatcher_.getClient( id0 );
        assertTrue( client0 != null );
        assertTrue( client0.reg_ );
        assertTrue( client0.unreg_ );
        assertEquals( meta0, client0.meta_ );
        assertEquals( subs0, client0.subs_ );

        // Check that the client watcher has received hub event messages 
        // concerning itself as well.
        String cwId = clientWatcher_.getConnection().getRegInfo().getSelfId();
        WatchedClient cwClient = clientWatcher_.getClient( cwId ); 
        assertTrue( cwClient != null );
        assertTrue( ! cwClient.unreg_ );
        assertEquals( clientWatcher_.getMetadata(), cwClient.meta_ );
        assertEquals( clientWatcher_.getSubscriptions(), cwClient.subs_ );

        // Tidy up.
        c3.unregister();
        assertTestClients( c1, new String[] { id2, } );
        assertTestClients( c2, new String[] { id1, } );

        c1.unregister();
        c2.unregister();
    }

    /**
     * Runs a lot of clients throwing a lot of messages at each other
     * simultaneously.
     */
    private void testStress() throws IOException {
        ClientProfile profile = new ClientProfile() {
            public HubConnection register() throws SampException {
                return HubTester.this.register();
            }
        };
        new CalcStorm( profile, random_, 10, 20, Calculator.RANDOM_MODE )
           .run();
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
        StringBuffer sbuf = new StringBuffer( nchar );
        char[] chrs = ugly ? GENERAL_CHARS : ALPHA_CHARS;
        for ( int i = 0; i < nchar; i++ ) {
            sbuf.append( chrs[ random_.nextInt( chrs.length ) ] );
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
     * Returns a character array containing each distinct alphanumeric 
     * character.
     *
     * @return  array of alphanumeric characters
     */
    private static char[] createAlphaCharacters() {
        StringBuffer sbuf = new StringBuffer();
        for ( char c = 'A'; c <= 'Z'; c++ ) {
            sbuf.append( c );
        }
        for ( char c = '0'; c <= '9'; c++ ) {
            sbuf.append( c );
        }
        return sbuf.toString().toCharArray();
    }

    /**
     * Returns a character array containing every character which is legal
     * for inclusion in a SAMP <code>string</code>.
     *
     * @return  array of string characters
     */
    private static char[] createGeneralCharacters() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( (char) 0x09 );
        sbuf.append( (char) 0x0a );

        // Character 0x0d is problematic.  Although it is permissible to 
        // transmit this in an XML document, it can get transformed to 
        // 0x0a or (if adjacent to an existing 0x0a) elided.
        // The correct thing to do probably would be to note in the standard
        // that all bets are off when transmitting line end characters -
        // but sending a line-end will probably end up as a line-end.
        // However I can't be bothered to start up a new thread about this
        // on the apps-samp list, so for the purposes of this test just
        // avoid sending it.
     // sbuf.append( (char) 0x0d );
        for ( char c = 0x20; c <= 0x7f; c++ ) {
            sbuf.append( c );
        }
        return sbuf.toString().toCharArray();
    }

    /**
     * Main method.  Tests a hub which is currently running.
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }
   
    /**
     * Does the work for the main method.
     * Use -help flag.
     */
    public static int runMain( String[] args ) throws IOException {
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( HubTester.class.getName() )
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n           " )
            .append( " [-gui]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        boolean gui = false;
        int verbAdjust = 0;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-gui" ) ) {
                it.remove();
                gui = true;
            }
            else if ( arg.equals( "-nogui" ) ) {
                it.remove();
                gui = false;
            }
            else if ( arg.startsWith( "-v" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.startsWith( "+v" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.startsWith( "-h" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
            else {
                it.remove();
                System.err.println( usage );
                return 1;
            }
        }
        assert argList.isEmpty();

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Get profile.
        ClientProfile profile = DefaultClientProfile.getProfile();

        // Set up GUI monitor if required.
        JFrame frame;
        if ( gui ) {
            frame = new JFrame( "HubTester Monitor" );
            frame.getContentPane().add( new HubMonitor( profile, true, 1 ) );
            frame.pack();
            frame.setVisible( true );
        }
        else {
            frame = null;
        }
        new HubTester( profile ).run();
        if ( frame != null ) {
            frame.dispose();
        }
        return 0;
    }

    /**
     * CallableClient implementation for testing.
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

            // Insert the message ID into the response if requested to do so.
            String msgIdQuery = (String) msg.get( MSGIDQUERY_KEY );
            if ( msgIdQuery != null && SampUtils.decodeBoolean( msgIdQuery ) ) {
                response.put( MSGIDQUERY_KEY, msgId );
            }
            response.check();

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
                synchronized ( this ) {
                    pingCount_++;
                }
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
            subs.check();
            return subs;
        }
    }

    /**
     * CallableClient implementation which watches hub.event messages
     * concerning the registration and attributes of other clients.
     */
    private static class ClientWatcher implements CallableClient {

        private final HubConnection connection_;
        private final Map clientMap_;
        private Throwable error_;

        /**
         * Constructor.
         *
         * @param  connection  hub connection
         */
        ClientWatcher( HubConnection connection ) {
            connection_ = connection;
            clientMap_ = Collections.synchronizedMap( new HashMap() );
        }

        /**
         * Returns a WatchedClient object corresponding to a given client
         * public ID.  This will contain information about the hub event
         * messages this watcher has received concerning that client up till
         * now.
         *
         * @param  id  public id of a client which has been registered
         * @return  watchedClient object if any messages have been received
         *          about <code>id</code>, otherwise null
         */
        public WatchedClient getClient( String id ) {
            return (WatchedClient) clientMap_.get( id ); 
        }

        /**
         * Returns an error if any error has been thrown during processing 
         * of hub event messages.
         *
         * @return   deferred throwable, or null in case of no problems
         */
        public Throwable getError() {
            return error_;
        }

        /**
         * Returns the hub connection used by this client.
         *
         * @return  hub connection
         */
        public HubConnection getConnection() {
            return connection_;
        }

        public void receiveCall( String senderId, String msgId, Message msg ) {
            receiveNotification( senderId, msg );
            Response response =
                error_ == null
                    ? Response.createSuccessResponse( new HashMap() )
                    : Response.createErrorResponse( new ErrInfo( "broken" ) );
            try {
                connection_.reply( msgId, response );
            }
            catch ( SampException e ) {
                error_ = e;
            }
        }

        public void receiveNotification( String senderId, Message msg ) {
            if ( error_ == null ) {
                try {
                    processMessage( senderId, msg );
                }
                catch ( Throwable e ) {
                    error_ = e;
                }
            }
        }

        private void processMessage( String senderId, Message msg )
                throws IOException {

            // Check the message actually comes from the hub.
            assertEquals( senderId, connection_.getRegInfo().getHubId() );
            String mtype = msg.getMType();
            Map params = msg.getParams();

            // Get (if necessary lazily creating) a WatchedClient object
            // which this message concerns.
            String id = (String) msg.getParam( "id" );
            assertTrue( id != null );
            synchronized ( clientMap_ ) {
                if ( ! clientMap_.containsKey( id ) ) {
                    clientMap_.put( id, new WatchedClient() );
                }
                WatchedClient client = (WatchedClient) clientMap_.get( id );

                // Handle the various hub event messages by updating fields of
                // the right WatchedClient object.
                if ( REGISTER_MTYPE.equals( mtype ) ) {
                    assertTrue( ! client.reg_ );
                    client.reg_ = true;
                }
                else if ( UNREGISTER_MTYPE.equals( mtype ) ) {
                    assertTrue( ! client.unreg_ );
                    client.unreg_ = true;
                }
                else if ( METADATA_MTYPE.equals( mtype ) ) {
                    assertTrue( params.containsKey( "metadata" ) );
                    Metadata meta =
                        Metadata
                       .asMetadata( (Map) params.get( "metadata" ) );
                    meta.check();
                    client.meta_ = meta;
                }
                else if ( SUBSCRIPTIONS_MTYPE.equals( mtype ) ) {
                    assertTrue( params.containsKey( "subscriptions" ) );
                    Subscriptions subs =
                        Subscriptions
                       .asSubscriptions( (Map) params.get( "subscriptions" ) );
                    subs.check();
                    client.subs_ = subs;
                }
                else {
                    fail();
                }
                clientMap_.notifyAll();
            }
        }

        public void receiveResponse( String responderId, String msgTag,
                                      Response response ) {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a suitable subscriptions object for this client.
         *
         * @return  subscriptions
         */
        public static Subscriptions getSubscriptions() {
            Subscriptions subs = new Subscriptions();
            subs.addMType( REGISTER_MTYPE );
            subs.addMType( UNREGISTER_MTYPE );
            subs.addMType( METADATA_MTYPE );
            subs.addMType( SUBSCRIPTIONS_MTYPE );
            subs.check();
            return subs;
        }

        /**
         * Returns a suitable metadata object for this client.
         */
        public static Metadata getMetadata() {
            Metadata meta = new Metadata();
            meta.setName( "ClientWatcher" );
            meta.setDescriptionText( "Tracks other clients for HubTester" );
            meta.check();
            return meta;
        }
    }

    /**
     * Struct-type utility class which aggregates mutable information about
     * a client, to be updated in response to hub event messages.
     */
    private static class WatchedClient {

        /** Whether this client has ever been registered. */
        boolean reg_;

        /** Whether this clent has ever been unregistered. */
        boolean unreg_;

        /** Current metadata object for this client. */
        Metadata meta_;

        /** Current subscriptions object for this client. */
        Subscriptions subs_;
    }
}
