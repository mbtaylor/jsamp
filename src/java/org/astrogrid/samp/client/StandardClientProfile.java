package org.astrogrid.samp.client;

import java.io.IOException;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.xmlrpc.ApacheClient;
import org.astrogrid.samp.xmlrpc.ApacheServerFactory;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServerFactory;

/**
 * Standard Profile implementation of ClientProfile.
 * This class is a singleton.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class StandardClientProfile implements ClientProfile {

    private final SampXmlRpcClient xClient_;
    private final SampXmlRpcServerFactory xServerFactory_;

    /** Sole instance. */
    private static final StandardClientProfile apacheInstance_ =
        new StandardClientProfile( new ApacheClient(),
                                   new ApacheServerFactory() );

    /**
     * Private constructor.
     */
    public StandardClientProfile( SampXmlRpcClient xClient,
                                  SampXmlRpcServerFactory xServerFactory ) {
        xClient_ = xClient;
        xServerFactory_ = xServerFactory;
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
            return new XmlRpcHubConnection( xClient_, xServerFactory_,
                                            lockInfo.getXmlrpcUrl(),
                                            lockInfo.getSecret() );
        }
    }

    /**
     * Returns a working instance of this class.
     */
    public static StandardClientProfile getInstance() {
        return apacheInstance_;
    }
}
