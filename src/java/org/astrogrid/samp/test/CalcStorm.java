package org.astrogrid.samp.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.gui.HubMonitor;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;

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
    private final int nClient_;
    private final int nQuery_;
    private final Calculator.SendMode sendMode_;

    /**
     * Constructor.
     *
     * @param  profile  hub connection factory
     * @param  random   random number generator
     * @param  nClient  number of clients to run
     * @param  nQuery   number of messages each client will send
     * @param  sendMode delivery pattern for messages
     */
    public CalcStorm( ClientProfile profile, Random random, int nClient,
                      int nQuery, Calculator.SendMode sendMode ) {
        profile_ = profile;
        random_ = random;
        nClient_ = nClient;
        nQuery_ = nQuery;
        sendMode_ = sendMode;
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
            if ( conn == null ) {
                throw new IOException( "No hub is running" );
            }
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
                            calc.sendMessage( ids[ random.nextInt( nClient_ ) ],
                                              sendMode_ );
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

        // If we are using the notification delivery pattern, wait until 
        // all the clients have received all the messages they are expecting. 
        // In the case of call/response this is not necessary, since the
        // message sender threads will only complete their run() methods 
        // when the responses have come back, which must mean that the
        // messages arrived at their recipients.
        if ( sendMode_ == Calculator.NOTIFY_MODE ||
             sendMode_ == Calculator.RANDOM_MODE ) {
            for ( boolean done = false; ! done; ) {
                int totCalc = 0;
                for ( int ic = 0; ic < nClient_; ic++ ) {
                    totCalc += calcs[ ic ].getReceiveCount();
                }
                done = totCalc >= nClient_ * nQuery_;
                if ( ! done ) {
                    Thread.yield();
                }
            }
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

    /**
     * Does the work for the main method.
     * Use -help flag for documentation.
     *
     * @param  args  command-line arguments
     * @return  0 means success
     */
    public static int runMain( String[] args ) throws IOException {

        // Set up usage message.
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( CalcStorm.class.getName() )
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( " [-gui]" )
            .append( "\n           " )
            .append( " [-nclient <n>]" )
            .append( " [-nquery <n>]" )
            .append( " [-mode sync|async|notify|random]" )
            .append( "\n" )
            .toString();

        // Prepare default values for test.
        ClientProfile profile = StandardClientProfile.getInstance();
        Random random = new Random( 2333333 );
        int nClient = 20;
        int nQuery = 100;
        Calculator.SendMode sendMode = Calculator.RANDOM_MODE;
        int verbAdjust = 0;
        boolean gui = false;

        // Parse arguments, modifying test parameters as appropriate.
        List argList = new ArrayList( Arrays.asList( args ) );
        try {
            for ( Iterator it = argList.iterator(); it.hasNext(); ) {
                String arg = (String) it.next();
                if ( arg.startsWith( "-nc" ) && it.hasNext() ) {
                    it.remove();
                    String snc = (String) it.next();
                    it.remove();
                    nClient = Integer.parseInt( snc );
                }
                else if ( arg.startsWith( "-nq" ) && it.hasNext() ) {
                    it.remove();
                    String snq = (String) it.next();
                    it.remove();
                    nQuery = Integer.parseInt( snq );
                }
                else if ( arg.equals( "-mode" ) && it.hasNext() ) {
                    it.remove();
                    String smode = (String) it.next();
                    it.remove();
                    final Calculator.SendMode sm;
                    if ( smode.toLowerCase().startsWith( "sync" ) ) {
                        sm = Calculator.SYNCH_MODE;
                    }
                    else if ( smode.toLowerCase().startsWith( "async" ) ) {
                        sm = Calculator.ASYNCH_MODE;
                    }
                    else if ( smode.toLowerCase().startsWith( "notif" ) ) {
                        sm = Calculator.NOTIFY_MODE;
                    }
                    else if ( smode.toLowerCase().startsWith( "rand" ) ) {
                        sm = Calculator.RANDOM_MODE;
                    }
                    else {
                        System.err.println( usage );
                        return 1;
                    }
                    sendMode = sm;
                }
                else if ( arg.equals( "-gui" ) ) {
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
                    System.out.println( usage );
                    return 0;
                }
                else {
                    System.err.println( usage );
                    return 1;
                }
            }
        }
        catch ( RuntimeException e ) {
            System.err.println( usage );
            return 1;
        }
        if ( ! argList.isEmpty() ) {
            System.err.println( usage );
            return 1;
        }

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Set up GUI monitor if required.
        JFrame frame;
        if ( gui ) {
            frame = new JFrame( "CalcStorm Monitor" );
            frame.getContentPane().add( new HubMonitor( profile, 1 ) );
            frame.pack();
            frame.setVisible( true );
        }
        else {
            frame = null;
        }

        // Run the test.
        long start = System.currentTimeMillis();
        new CalcStorm( profile, random, nClient, nQuery, sendMode ).run();
        long time = System.currentTimeMillis() - start;
        System.out.println( "Elapsed time: " + time + " ms" 
                          + " (" + (int) ( time * 1000. / ( nClient * nQuery ) )
                          + " us per message)" );

        // Tidy up and return.
        if ( frame != null ) {
            frame.dispose();
        }
        return 0;
    }

    /**
     * Main method.  Use -help flag.
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }
}
