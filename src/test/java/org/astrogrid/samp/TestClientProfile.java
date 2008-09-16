package org.astrogrid.samp;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.xmlrpc.HubRunner;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcHubConnection;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Client profile implementation for use with test cases.
 * As well as providing a test of the package's pluggability,
 * it means that test cases can run without having to worry about
 * whether an actual (Standard Profile) SAMP hub is present.
 *
 * @author   Mark Taylor
 * @since    29 Jul 2008 
 */ 
public class TestClientProfile implements ClientProfile {

    private final File lockFile_;
    private final Random random_;
    private final SampXmlRpcClient hubClient_;
    private final SampXmlRpcServerFactory hubServerFactory_;
    private final SampXmlRpcClient clientClient_;
    private final SampXmlRpcServerFactory clientServerFactory_;
    private HubRunner hubRunner_;

    public TestClientProfile( Random random, XmlRpcKit xmlrpc ) {
        this( random, xmlrpc.getClient(), xmlrpc.getServerFactory(),
                      xmlrpc.getClient(), xmlrpc.getServerFactory() );
    }

    /**
     * Constructor.
     */
    public TestClientProfile( Random random,
                              SampXmlRpcClient hubClient,
                              SampXmlRpcServerFactory hubServerFactory,
                              SampXmlRpcClient clientClient,
                              SampXmlRpcServerFactory clientServerFactory ) {
        random_ = random;
        hubClient_ = hubClient;
        hubServerFactory_ = hubServerFactory;
        clientClient_ = clientClient;
        clientServerFactory_ = clientServerFactory;
        File dir = new File( System.getProperty( "user.dir", "." ) );
        try {
            lockFile_ = File.createTempFile( "samp", ".lock", dir );
            lockFile_.delete();
            lockFile_.deleteOnExit();
        }
        catch ( IOException e ) {
            throw new RuntimeException( e );
        }
    }

    /**
     * Starts a hub associated with this profile.
     */
    public synchronized void startHub() throws IOException {
        if ( hubRunner_ != null ) {
            throw new IllegalStateException();
        }
        HubService service = new BasicHubService( random_ );
        hubRunner_ =
            new HubRunner( hubClient_, hubServerFactory_, service, lockFile_ );
        hubRunner_.start();
    }

    /** 
     * Stops the hub associated with this profile.
     */
    public synchronized void stopHub() {
        if ( hubRunner_ == null ) {
            throw new IllegalStateException();
        }
        hubRunner_.shutdown();
        hubRunner_ = null;
    }

    public HubConnection register() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo = LockInfo.readLockFile( lockFile_ );
            if ( lockInfo == null ) {
                return null;
            }
            else {
                try {
                    lockInfo.check();
                }
                catch ( DataException e ) {
                    return null;
                }
                return new XmlRpcHubConnection( clientClient_,
                                                clientServerFactory_,
                                                lockInfo.getXmlrpcUrl(),
                                                lockInfo.getSecret() );
            }
        }
        catch ( IOException e ) {
            throw new SampException( e );
        }
    }

    public static TestClientProfile[] getTestProfiles( Random random ) {
        SampXmlRpcClient aClient = XmlRpcKit.APACHE.getClient();
        SampXmlRpcServerFactory aServ = XmlRpcKit.APACHE.getServerFactory();
        SampXmlRpcClient iClient = XmlRpcKit.INTERNAL.getClient();
        SampXmlRpcServerFactory iServ = XmlRpcKit.INTERNAL.getServerFactory();
        return new TestClientProfile[] {
            new TestClientProfile( random, aClient, aServ, iClient, iServ ),
            new TestClientProfile( random, iClient, iServ, aClient, aServ ),
            new TestClientProfile( random, iClient, iServ, iClient, iServ ),
        };
    }
}
