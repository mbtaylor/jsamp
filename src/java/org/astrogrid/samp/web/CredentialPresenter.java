package org.astrogrid.samp.web;

import java.util.Map;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * Extracts credentials for presentation to the user from available
 * information, so that the user can decide whether to allow registration.
 *
 * @author   Mark Taylor
 * @since    18 Jun 2016
 */
public interface CredentialPresenter {

    /**
     * Returns an object which contains user-directed credential messages,
     * given available information from the registration request.
     * If the request or securityMap can be determined to be
     * definitely unsuitable for registration, a SampException is thrown.
     *
     * @param  request  HTTP request associated with the registration request
     * @param  securityMap  information explicitly supplied by the aspiring
     *                      client in support of its application to register
     * @param   authContent  content of AuthResourceBundle bundle
     * @return    items for presentation to the user
     * @throws  SampException  if credentials should definitely not be accepted
     */
    Presentation createPresentation( HttpServer.Request request,
                                     Map securityMap,
                                     AuthResourceBundle.Content authContent )
            throws SampException;

    /**
     * Aggregates credential information to be presented to the user.
     */
    interface Presentation {

        /**
         * Returns an ordered map of String-&gt;String entries
         * containing name, value pairs.
         *
         * @return  map with ordered entries
         */
        Map getAuthEntries();

        /**
         * Returns an array of "message" objects providing additional
         * information for the user.
         *
         * <p>If the supplied identity information looks OK,
         * then returning an empty array is a good idea.
         * But if there is some kind of inconsistency or cause for alarm,
         * a sequence of GUI elements may be returned.
         *
         * <p>The return value is suitable for use as the <code>msg</code>
         * argument of one of <code>JOptionPane</code>'s methods.
         *
         * @return   message array describing the applicant to the user
         * @see   javax.swing.JOptionPane
         */
        Object[] getAuthLines();
    }
}
