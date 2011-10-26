package org.astrogrid.samp;

import java.io.IOException;
import java.util.Random;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.Hub;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.ProfileToken;
import org.astrogrid.samp.web.WebTestProfile;
import org.astrogrid.samp.xmlrpc.StandardTestProfile;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClientFactory;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Abstract class providing both a hub and a client profile;
 * the client profile talks to the hub.
 * This can be used for isolated testing of hub/client profile pairs.
 * For use within a test framework implementations should normally take
 * care not to use the resources (well-known lockfile locations, 
 * well-known ports etc) which are commonly used for normal SAMP operations.
 *
 * @author   Mark Taylor
 * @since    4 Feb 2011
 */
public abstract class TestProfile implements ClientProfile {

    private final Random random_;
    private final ProfileToken directToken_;
    private Hub hub_;
    private HubService service_;

    protected TestProfile( Random random ) {
        random_ = random;
        directToken_ = new ProfileToken() {
            public String getProfileName() {
                return "<direct>";
            }
        };
    }

    public synchronized void startHub() throws IOException {
        if ( hub_ != null ) {
            throw new IllegalStateException( "Hub not stopped "
                                           + "due to earlier test failure?" );
        }
        service_ = new BasicHubService( createRandom() );
        hub_ = new Hub( service_ );
        service_.start();
        hub_.startProfile( createHubProfile() );
    }

    public synchronized void stopHub() {
        if ( hub_ == null ) {
            throw new IllegalStateException();
        }
        hub_.shutdown();
        hub_ = null;
        service_ = null;
    }

    public HubConnection registerDirect() throws SampException {
        return service_.register( directToken_ );
    }

    public abstract HubProfile createHubProfile() throws IOException;

    public Random createRandom() {
        return new Random( random_.nextLong() );
    }

    /**
     * Returns an array of TestProfiles suitable for performing tests on.
     */
    public static TestProfile[] createTestProfiles( Random random )
            throws IOException {
        SampXmlRpcClientFactory aClient =
            XmlRpcKit.APACHE.getClientFactory();
        SampXmlRpcServerFactory aServ =
            XmlRpcKit.APACHE.getServerFactory();
        SampXmlRpcClientFactory iClient =
            XmlRpcKit.INTERNAL.getClientFactory();
        SampXmlRpcServerFactory iServ =
            XmlRpcKit.INTERNAL.getServerFactory();
        return new TestProfile[] {
            new StandardTestProfile( random, aClient, aServ, iClient, iServ ),
            new StandardTestProfile( random, iClient, iServ, aClient, aServ ),
            new StandardTestProfile( random, iClient, iServ, iClient, iServ ),
            new WebTestProfile( random, true, null ),
        };
    }
}
