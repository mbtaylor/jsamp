package org.astrogrid.samp;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.Platform;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.StandardClientProfile;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.HubRunner;
import org.astrogrid.samp.hub.HubService;
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
    private final TestClientProfile profile_ = new TestClientProfile( random_ );

    protected void setUp() throws IOException {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( SampXmlRpcHandler.class.getName() )
              .setLevel( Level.SEVERE );
        profile_.startHub();
    }

    protected void tearDown() {
        profile_.stopHub();
    }

    public void testHubTester() throws Exception {
        new HubTester( profile_ ).run();
    }
  
    public void testCalcStorm() throws IOException {
        new CalcStorm( profile_, random_, 10, 20, Calculator.RANDOM_MODE )
           .run();
    }
}
