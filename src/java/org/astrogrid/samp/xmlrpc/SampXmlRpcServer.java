package org.astrogrid.samp.xmlrpc;

import java.net.URL;

/**
 * Interface for a server which can respond to XML-RPC calls for SAMP.
 * The method parameters and return values must be of SAMP-compatible types,
 * that is only Strings, Lists, and String-keyed Maps are allowed in
 * the data structures.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public interface SampXmlRpcServer {

    /**
     * Returns the server's endpoint.
     *
     * @return  URL to which XML-RPC requests are POSTed
     */
    URL getEndpoint();

    /**
     * Adds a handler which can service certain XML-RPC methods.
     *
     * @param  handler  handler to add
     */
    void addHandler( SampXmlRpcHandler handler );

    /**
     * Removes a previously-added handler.
     *
     * @param  handler  handler to remove
     */
    void removeHandler( SampXmlRpcHandler handler );
}
