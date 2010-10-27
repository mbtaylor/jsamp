package org.astrogrid.samp.xmlrpc;

import java.util.List;

/**
 * Interface for an object which can process certain XML-RPC requests.
 * Used by {@link SampXmlRpcServer}.
 *
 * @author   Mark Taylor    
 * @since    22 Aug 2008
 */
public interface SampXmlRpcHandler {

    /**
     * Returns true if this handler should be able to process 
     * given XML-RPC method.
     *
     * @param  method  method name
     */
    boolean canHandleCall( String method );

    /**
     * Processes an XML-RPC call.  This method should only be called if
     * {@link #canHandleCall canHandleCall(method)} returns true.
     * The <code>params</code> list and the return value must be 
     * SAMP-compatible, that is only Strings, Lists and String-keyed Maps
     * are allowed in the data structures.
     * The <code>reqInfo</code> parameter may be used to provide additional
     * information about the XML-RPC request, for instance the originating
     * host; this is implementation specific, and may be null.
     *
     * @param   method   XML-RPC method name
     * @param   params   XML-RPC parameter list (SAMP-compatible)
     * @param   reqInfo  optional additional request information; may be null
     * @return   return value (SAMP-compatible)
     */
    Object handleCall( String method, List params, Object reqInfo )
            throws Exception;
}
