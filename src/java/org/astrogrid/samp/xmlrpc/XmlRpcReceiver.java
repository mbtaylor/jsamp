package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.hub.HubServiceException;
import org.astrogrid.samp.hub.Receiver;

/**
 * Receiver implementation used to communicate with XML-RPC-based callable
 * clients.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
class XmlRpcReceiver implements Receiver {

    private final String privateKey_;
    private final String endpoint_;
    private final SampXmlRpcClient xClient_;

    /**
     * Constructor.
     *
     * @param  xClient  XML-RPC client implementation
     * @param  SAMP client's private key
     * @param  url  XML-RPC endpoint for calling the SAMP client
     */
    public XmlRpcReceiver( SampXmlRpcClient xClient, String privateKey,
                           URL url ) {
        xClient_ = xClient;
        privateKey_ = privateKey;
        endpoint_ = url.toString();
    }

    public void receiveCall( String senderId, String msgId, Map message )
            throws HubServiceException {
        exec( "receiveCall", new Object[] { senderId, msgId, message, } );
    }

    public void receiveNotification( String senderId, Map message )
            throws HubServiceException {
        exec( "receiveNotification", new Object[] { senderId, message, } );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Map response )
            throws HubServiceException {
        exec( "receiveResponse",
              new Object[] { responderId, msgTag, response, } );
    }

    /**
     * Makes an XML-RPC call to the SAMP callable client represented 
     * by this receiver.
     *
     * @param   methodName  unqualified SAMP callable client API method name
     * @param   params   array of method parameters
     * @return  XML-RPC call return value
     */
    private void exec( String methodName, Object[] params )
            throws HubServiceException {
        List paramList = new ArrayList();
        paramList.add( privateKey_ );
        for ( int ip = 0; ip < params.length; ip++ ) {
            paramList.add( params[ ip ] );
        }
        try {
            rawExec( "samp.client." + methodName, paramList );
        }
        catch ( IOException e ) {
            throw new HubServiceException( e.getMessage(), e );
        }
    }

    /**
     * Actually makes an XML-RPC call to the SAMP callable client 
     * represented by this receiver.
     *
     * @param   fqName  fully qualified SAMP callable client API method name
     * @param   paramList   list of method parameters
     * @return  XML-RPC call return value
     */
    private void rawExec( String fqName, List paramList ) throws IOException {
        xClient_.callAndForget( endpoint_, fqName, paramList );
    }
}
