package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Interface for a client which can make XML-RPC calls for SAMP.
 * The method parameters and return values must be of SAMP-compatible types,
 * that is only Strings, Lists, and String-keyed Maps are allowed in
 * the data structures.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public interface SampXmlRpcClient {

    /**
     * Makes a synchronous call, waiting for the response and returning
     * the result.
     *
     * @param  method    XML-RPC method name
     * @param  params    parameters for XML-RPC call (SAMP-compatible)
     * @return   XML-RPC call return value (SAMP-compatible)
     */
    Object callAndWait( String method, List params ) throws IOException;

    /**
     * Sends a call, but does not wait around for the response.
     * If possible, this method should complete quickly.
     *
     * @param  method    XML-RPC method name
     * @param  params    parameters for XML-RPC call (SAMP-compatible)
     */
    void callAndForget( String method, List params ) throws IOException;
}
