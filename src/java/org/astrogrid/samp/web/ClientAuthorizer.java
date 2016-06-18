package org.astrogrid.samp.web;

import java.util.Map;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * Defines authorization functionality which is used to determine whether
 * a client is permitted to register with the hub.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public interface ClientAuthorizer {

    /**
     * Indicates whether an HTTP request representing an otherwise
     * unauthorized connection attempt will be permitted access to
     * sensitive system resources.  If so, the method exits normally.
     * If authorization is denied, a SampException is thrown,
     * with a message that indicates the reason for denial.
     *
     * @param   request   incoming HTTP request
     * @param   securityMap   credential items supplied explicitly by
     *                        aspiring client to support its registration
     *                        request
     * @throws   SampException  with reason if authorization is denied
     */
    void authorize( HttpServer.Request request, Map securityMap )
            throws SampException;
}
