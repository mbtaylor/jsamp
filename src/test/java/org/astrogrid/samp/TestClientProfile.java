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
import org.astrogrid.samp.xmlrpc.ApacheClient;
import org.astrogrid.samp.xmlrpc.ApacheServerFactory;
import org.astrogrid.samp.xmlrpc.HubRunner;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcHubConnection;

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
    private final SampXmlRpcServerFactory xServerFactory_;
    private final SampXmlRpcClient xClient_;
    private HubRunner hubRunner_;

    /**
     * Constructor.
     */
    public TestClientProfile( Random random ) {
        random_ = random;
        xServerFactory_ = new ApacheServerFactory();
        xClient_ = new ApacheClient();
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
            new HubRunner( xClient_, xServerFactory_, service, lockFile_ );
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
                lockInfo.check();
                return new XmlRpcHubConnection( xClient_, xServerFactory_,
                                                lockInfo.getXmlrpcUrl(),
                                                lockInfo.getSecret() );
            }
        }
        catch ( IOException e ) {
            throw new SampException( e );
        }
    }
}
