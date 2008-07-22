package org.astrogrid.samp.test;

import java.io.IOException;
import java.util.Random;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;

/**
 * Runs a load of Calculator clients at once all sending messages to each other.
 * Suitable for load testing or benchmarking a hub.
 *
 * @author   Mark Taylor
 * @since    22 Jul 2008
 */
public class CalcStorm {

    private final ClientProfile profile_;
    private final Random random_;
    private int nClient_;
    private int nQuery_;

    /**
     * Constructor.
     *
     * @param  profile  hub connection factory
     * @param  random   random number generator
     * @param  nClient  number of clients to run
     * @param  nQuery   number of messages each client will send
     */
    public CalcStorm( ClientProfile profile, Random random, int nClient,
                      int nQuery ) {
        profile_ = profile;
        random_ = random;
        nClient_ = nClient;
        nQuery_ = nQuery;
    }

    /**
     * Runs a lot of calculators at once all talking to each other.
     *
     * @throws  TestException  if any tests fail
     */
    public void run() throws IOException {

        // Set up clients.
        final Calculator[] calcs = new Calculator[ nClient_ ];
        final String[] ids = new String[ nClient_ ];
        final Random[] randoms = new Random[ nClient_ ];
        for ( int ic = 0; ic < nClient_; ic++ ) {
            HubConnection conn = profile_.register();
            randoms[ ic ] = new Random( random_.nextLong() );
            ids[ ic ] = conn.getRegInfo().getSelfId();
            calcs[ ic ] = new Calculator( conn, randoms[ ic ] );
        }

        // Set up one thread per client to do the message sending.
        Thread[] calcThreads = new Thread[ nClient_ ];
        final Throwable[] errors = new Throwable[ 1 ];
        for ( int ic = 0; ic < nClient_; ic++ ) {
            final Calculator calc = calcs[ ic ];
            final Random random = randoms[ ic ];
            calcThreads[ ic ] = new Thread( "Calc" + ic ) {
                public void run() {
                    try {
                        for ( int iq = 0; iq < nQuery_ && errors[ 0 ] == null;
                              iq++ ) {
                            calc.sendMessage( ids[ random
                                                  .nextInt( nClient_ ) ] );
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
        for ( int ic = 0; ic < nClient_; ic++ ) {
            calcThreads[ ic ].start();
        }

        // Wait for all the threads to finish.
        try {
            for ( int ic = 0; ic < nClient_; ic++ ) {
                calcThreads[ ic ].join();
            }
        }
        catch ( InterruptedException e ) {
            throw new TestException( "Interrupted", e );
        }

        // Unregister the clients.
        for ( int ic = 0; ic < nClient_; ic++ ) {
            calcs[ ic ].getConnection().unregister();
        }

        // If any errors occurred on the sending thread, rethrow one of them
        // here.
        if ( errors[ 0 ] != null ) {
            throw new TestException( "Error in calculator thread: "
                                   + errors[ 0 ].getMessage(),
                                     errors[ 0 ] );
        }

        // Check that the number of messages sent and the number received
        // was what it should have been.
        int totCalc = 0;
        for ( int ic = 0; ic < nClient_; ic++ ) {
            Calculator calc = calcs[ ic ];
            Tester.assertEquals( nQuery_, calc.getSendCount() );
            totCalc += calc.getReceiveCount();
        }
        Tester.assertEquals( totCalc, nClient_ * nQuery_ );
    }
}
