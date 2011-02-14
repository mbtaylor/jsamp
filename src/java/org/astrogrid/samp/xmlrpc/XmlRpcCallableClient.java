package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.SampException;

/**
 * CallableClient implementation used to communicate with XML-RPC-based
 * callable clients.
 *
 * @author   Mark Taylor
 * @since    28 Jan 2011
 */
class XmlRpcCallableClient implements CallableClient {

    private final SampXmlRpcClient xClient_;
    private final String privateKey_;

    /**
     * Constructor.
     *
     * @param  xClient  XML-RPC client implementation
     * @param  SAMP client's private key
     */
    public XmlRpcCallableClient( SampXmlRpcClient xClient, String privateKey ) {
        xClient_ = xClient;
        privateKey_ = privateKey;
    }

    public void receiveCall( String senderId, String msgId, Message msg )
            throws SampException {
        exec( "receiveCall", new Object[] { senderId, msgId, msg, } );
    }

    public void receiveNotification( String senderId, Message msg )
            throws SampException {
        exec( "receiveNotification", new Object[] { senderId, msg, } );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Response response )
            throws SampException {
        exec( "receiveResponse",
              new Object[] { responderId, msgTag, response, } );
    }

    /**
     * Makes an XML-RPC call to the SAMP callable client represented
     * by this receiver.
     *
     * @param   methodName  unqualified SAMP callable client API method name
     * @param   params   array of method parameters
     */
    private void exec( String methodName, Object[] params )
            throws SampException {
        List paramList = new ArrayList();
        paramList.add( privateKey_ );
        for ( int ip = 0; ip < params.length; ip++ ) {
            paramList.add( params[ ip ] );
        }
        try {
            rawExec( "samp.client." + methodName, paramList );
        }
        catch ( IOException e ) {
            throw new SampException( e.getMessage(), e );
        }
    }

    /**
     * Actually makes an XML-RPC call to the SAMP callable client
     * represented by this receiver.
     *
     * @param   fqName  fully qualified SAMP callable client API method name
     * @param   paramList   list of method parameters
     */
    private void rawExec( String fqName, List paramList ) throws IOException {

        // callAndForget might seem adequate here.  However, any implementation
        // I've come up with for it presents problems (for instance incomplete
        // HTTP connections when run from a shutdown hook thread,
        // so hub fails to warn all clients of impending shutdown, though
        // hacks around this are possible).
        // It seems least problematic, and not dramatically slower,
        // just to do synchronous calls here.  This could cause trouble
        // if clients hang onto HTTP connections for long though.
        xClient_.callAndWait( fqName, paramList );
    }
}
