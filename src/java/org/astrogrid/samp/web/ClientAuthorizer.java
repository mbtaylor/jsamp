package org.astrogrid.samp.web;

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
     * sensitive system resources.
     * The client submitting the request provides the 
     * <code>appName</code> parameter by way of additional information about
     * its identity.  However, the value of this name is supplied by the
     * (potentially malicious) applicant, so cannot in itself be regarded
     * as an additional security measure.
     *
     * @param   request   incoming HTTP request
     * @param   appName   name by which the application submitting the request
     *                    wishes to be known
     * @return  true iff submitter of the request should be permitted access
     *          to sensitive system resources in the future
     */
    boolean authorize( HttpServer.Request request, String appName );
}
