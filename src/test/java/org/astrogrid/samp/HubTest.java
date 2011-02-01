package org.astrogrid.samp;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.test.CalcStorm;
import org.astrogrid.samp.test.Calculator;
import org.astrogrid.samp.test.HubTester;

/**
 * Hub test case.
 * Most of the hard work is done by the hub test classes which are part of
 * the Java SAMP distribution itself, which know how to put a third-party 
 * hub through its paces.
 * This class mainly just starts up a hub and invokes those tests.
 *
 * @author   Mark Taylor
 * @since    29 Jul 2008
 */
public class HubTest extends TestCase {

    private final Random random_ = new Random( 23 );
    private final TestClientProfile[] profiles_ =
        TestClientProfile.getTestProfiles( random_ );

    protected void setUp() throws IOException {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.SEVERE );
    }

    public void testHubTester() throws Exception {
        for ( int i = 0; i < profiles_.length; i++ ) {
            TestClientProfile profile = profiles_[ i ];
            profile.startHub();
            new HubTester( profile ).run();
            new CalcStorm( profile, random_, 8, 8, Calculator.RANDOM_MODE )
               .run();
            profile.stopHub();
        }
    }
}
