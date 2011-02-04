package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.astrogrid.samp.TestProfile;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.HubProfile;

/**
 * Test Profile implementation based on the Standard Profile.
 * It puts the lockfile in a non-standard place though, so it can
 * run without interfering with any Standard Profile hub that
 * happens to be running at the same time.
 *
 * @author   Mark Taylor
 * @since    4 Feb 2011
 */
public class StandardTestProfile extends TestProfile {

    private final Random random_;
    private final File lockFile_;
    private final SampXmlRpcClientFactory hubClientFactory_;
    private final SampXmlRpcServerFactory hubServerFactory_;
    private final SampXmlRpcClientFactory clientClientFactory_;
    private final SampXmlRpcServerFactory clientServerFactory_;
    private static StandardTestProfile[] testProfiles_;

    public StandardTestProfile( Random random, XmlRpcKit xmlrpc ) {
        this( random, xmlrpc.getClientFactory(), xmlrpc.getServerFactory(),
                      xmlrpc.getClientFactory(), xmlrpc.getServerFactory() );
    }

    public StandardTestProfile( Random random,
                                SampXmlRpcClientFactory hubClientFactory,
                                SampXmlRpcServerFactory hubServerFactory,
                                SampXmlRpcClientFactory clientClientFactory,
                                SampXmlRpcServerFactory clientServerFactory ) {
        super( random );
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

    public HubProfile createHubProfile() {
        return new StandardHubProfile( hubClientFactory_, hubServerFactory_,
                                       lockFile_,
                                       Long.toHexString( random_.nextLong() ) );
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
}
