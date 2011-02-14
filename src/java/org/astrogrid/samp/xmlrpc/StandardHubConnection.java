package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.util.Collections;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.SampException;

/**
 * HubConnection implementation for the Standard Profile.
 *
 * @author   Mark Taylor
 * @since    27 Oct 2010
 */
class StandardHubConnection extends XmlRpcHubConnection {

    private final SampXmlRpcServerFactory serverFactory_;
    private final String clientKey_;
    private CallableClientServer callableServer_;

    /**
     * Constructor.
     *
     * @param   xClient   XML-RPC client
     * @param   serverFactory  XML-RPC server factory implementation
     * @param   secret  samp.secret registration password
     */
    public StandardHubConnection( SampXmlRpcClient xClient,
                                  SampXmlRpcServerFactory serverFactory,
                                  String secret )
            throws SampException {
        super( xClient, "samp.hub.", Collections.singletonList( secret ) );
        clientKey_ = getRegInfo().getPrivateKey();
        serverFactory_ = serverFactory;
    }

    public Object getClientKey() {
        return clientKey_;
    }

    public void setCallable( CallableClient callable ) throws SampException {
        if ( callableServer_ == null ) {
            try {
                callableServer_ = CallableClientServer
                                 .getInstance( serverFactory_.getServer() );
            }
            catch ( IOException e ) {
                throw new SampException( "Can't start client XML-RPC server",
                                         e );
            }
        }
        callableServer_.addClient( this, callable );
        exec( "setXmlrpcCallback",
              new Object[] { callableServer_.getUrl().toString() } );
    }

    public void unregister() throws SampException {
        if ( callableServer_ != null ) {
            callableServer_.removeClient( this );
        }
        super.unregister();
    }
}
