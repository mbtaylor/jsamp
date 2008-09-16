package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Standard Profile implementation of ClientProfile.
 * It is normally appropriate to use one of the static methods
 * to obtain an instance based on a particular XML-RPC implementation.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class StandardClientProfile implements ClientProfile {

    private final SampXmlRpcClient xClient_;
    private final SampXmlRpcServerFactory xServerFactory_;

    private static StandardClientProfile defaultInstance_;
    
    /**
     * Constructor.
     *
     * @param   xClient   XML-RPC client implementation
     * @param   xServerFactory   XML-RPC server factory implementation
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
     * Returns an instance based on the default XML-RPC implementation.
     * This can be configured using system properties.
     *
     * @see   XmlRpcKit#getInstance
     * @return  a client profile instance
     */
    public static StandardClientProfile getInstance() {
        if ( defaultInstance_ == null ) {
            XmlRpcKit xmlrpc = XmlRpcKit.getInstance();
            defaultInstance_ =
                new StandardClientProfile( xmlrpc.getClient(),
                                           xmlrpc.getServerFactory() );
        }
        return defaultInstance_;
    }
}
