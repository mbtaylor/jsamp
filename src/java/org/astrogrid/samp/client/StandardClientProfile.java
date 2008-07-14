package org.astrogrid.samp.client;

import java.io.IOException;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.SampException;

public class StandardClientProfile implements ClientProfile {

    private static final StandardClientProfile instance_ =
        new StandardClientProfile();

    private StandardClientProfile() {
    }

    public HubConnection createHubConnection() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo = LockInfo.readLockFile();
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

    public static StandardClientProfile getInstance() {
        return instance_;
    }
}
