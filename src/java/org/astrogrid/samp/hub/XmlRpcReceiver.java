package org.astrogrid.samp.hub;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Vector;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampXmlRpcHandler;

/**
 * Reciever implementation used to communicate with XML-RPC-based callable
 * clients.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
class XmlRpcReceiver implements Receiver {

    private final String privateKey_;
    private final XmlRpcClient xClient_;

    /**
     * Constructor.
     *
     * @param  SAMP client's private key
     * @param  url  XML-RPC endpoint for calling the SAMP client
     */
    public XmlRpcReceiver( String privateKey, URL url ) {
        privateKey_ = privateKey;
        xClient_ = new XmlRpcClientLite( url );
    }

    public void receiveCall( String senderId, String msgId, Map message )
            throws SampException {
        exec( "receiveCall", new Object[] { senderId, msgId, message, } );
    }

    public void receiveNotification( String senderId, Map message )
            throws SampException {
        exec( "receiveNotification", new Object[] { senderId, message, } );
    }

    public void receiveResponse( String responderId, String msgTag,
                                 Map response )
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
     * @return  XML-RPC call return value
     */
    private Object exec( String methodName, Object[] params )
            throws SampException {
        Vector paramVec = new Vector();
        paramVec.add( privateKey_ );
        for ( int ip = 0; ip < params.length; ip++ ) {
            paramVec.add( SampXmlRpcHandler.toApache( params[ ip ] ) );
        }
        return rawExec( "samp.client." + methodName, paramVec );
    }

    /**
     * Actually makes an XML-RPC call to the SAMP callable client 
     * represented by this receiver.
     *
     * @param   fqName  fully qualified SAMP callable client API method name
     * @param   params  vector of method parameters
     * @return  XML-RPC call return value
     */
    private Object rawExec( String fqName, Vector paramVec )
            throws SampException {
        try {
            return xClient_.execute( fqName, paramVec );
        }
        catch ( XmlRpcException e ) {
            throw new SampException( e.getMessage(), e );
        }
        catch ( IOException e ) {
            throw new SampException( e.getMessage(), e );
        }
    }
}
