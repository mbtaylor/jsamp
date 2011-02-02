package org.astrogrid.samp.web;

/**
 * Controls which origins are authorized to perform cross-origin access
 * to resources.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public interface OriginAuthorizer {

    /**
     * Indicates whether a client with a given origin is permitted
     * to access resources.
     *
     * @param   origin   client Origin
     * @return   true iff access is permitted
     * @see   <a href="http://tools.ietf.org/html/draft-abarth-origin"
     *           >Web Origin concept</a>
     */
    boolean authorize( String origin );

    /**
     * Indicates whether clients from arbitrary origins (includeing none)
     * are permitted to access resources.
     *
     * @return  true iff access is permitted
     */
    boolean authorizeAll();
}
