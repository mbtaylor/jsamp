package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.Hub;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.HubService;

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
    private final SampXmlRpcClientFactory hubClientFactory_;
    private final SampXmlRpcServerFactory hubServerFactory_;
    private final SampXmlRpcClientFactory clientClientFactory_;
    private final SampXmlRpcServerFactory clientServerFactory_;
    private Hub hub_;
    private static TestClientProfile[] testProfiles_;

    public TestClientProfile( Random random, XmlRpcKit xmlrpc ) {
        this( random, xmlrpc.getClientFactory(), xmlrpc.getServerFactory(),
                      xmlrpc.getClientFactory(), xmlrpc.getServerFactory() );
    }

    /**
     * Constructor.
     */
    public TestClientProfile( Random random,
                              SampXmlRpcClientFactory hubClientFactory,
                              SampXmlRpcServerFactory hubServerFactory,
                              SampXmlRpcClientFactory clientClientFactory,
                              SampXmlRpcServerFactory clientServerFactory ) {
        random_ = random;
        hubClientFactory_ = hubClientFactory;
        hubServerFactory_ = hubServerFactory;
        clientClientFactory_ = clientClientFactory;
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
        if ( hub_ != null ) {
            throw new IllegalStateException( "Hub not stopped"
                                           + " due to earlier test failure?" );
        }
        HubService service = new BasicHubService( random_ );
        HubProfile hubProfile =
            new StandardHubProfile( hubClientFactory_, hubServerFactory_,
                                    lockFile_,
                                    StandardHubProfile.createSecret() );
        hub_ = new Hub( service, new HubProfile[] { hubProfile } );
        hub_.start();
    }

    /** 
     * Stops the hub associated with this profile.
     */
    public synchronized void stopHub() {
        if ( hub_ == null ) {
            throw new IllegalStateException();
        }
        hub_.shutdown();
        hub_ = null;
    }

    public boolean isHubRunning() {
        try {
            return LockInfo.readLockFile( SampUtils.fileToUrl( lockFile_ ) )
                   != null;
        }
        catch ( IOException e ) {
            return false;
        }
    }

    public HubConnection register() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo =
                LockInfo.readLockFile( SampUtils.fileToUrl( lockFile_ ) );
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
                SampXmlRpcClient xClient =
                    clientClientFactory_.createClient( lockInfo
                                                      .getXmlrpcUrl() );
                return new StandardHubConnection( xClient, clientServerFactory_,
                                                  lockInfo.getSecret() );
            }
        }
        catch ( IOException e ) {
            throw new SampException( e );
        }
    }

    public static TestClientProfile[] getTestProfiles( Random random ) {
        if ( testProfiles_ == null ) {
            SampXmlRpcClientFactory aClient =
                XmlRpcKit.APACHE.getClientFactory();
            SampXmlRpcServerFactory aServ =
                XmlRpcKit.APACHE.getServerFactory();
            SampXmlRpcClientFactory iClient =
                XmlRpcKit.INTERNAL.getClientFactory();
            SampXmlRpcServerFactory iServ =
                XmlRpcKit.INTERNAL.getServerFactory();
            testProfiles_ = new TestClientProfile[] {
                new TestClientProfile( random, aClient, aServ, iClient, iServ ),
                new TestClientProfile( random, iClient, iServ, aClient, aServ ),
                new TestClientProfile( random, iClient, iServ, iClient, iServ ),
            };
        }
        return testProfiles_;
    }
}