package org.astrogrid.samp.client;

import java.io.IOException;
import org.astrogrid.samp.LockInfo;

/**
 * Standard Profile implementation of ClientProfile.
 * This class is a singleton.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class StandardClientProfile implements ClientProfile {

    /** Sole instance. */
    private static final StandardClientProfile instance_ =
        new StandardClientProfile();

    /**
     * Private constructor.
     */
    private StandardClientProfile() {
    }

    public HubConnection register() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo = LockInfo.readLockFile();
        }
        catch ( SampException e ) {
            throw (SampException) e;
        }
        catch ( IOException e ) {
            throw new SampException( "Error reading lockfile", e );
        }
        if ( lockInfo == null ) {
            return null;
        }
        else {
            return new XmlRpcHubConnection( lockInfo.getXmlrpcUrl(),
                                            lockInfo.getSecret() );
        }
    }

    /**
     * Returns the sole instance of this class.
     */
    public static StandardClientProfile getInstance() {
        return instance_;
    }
}
