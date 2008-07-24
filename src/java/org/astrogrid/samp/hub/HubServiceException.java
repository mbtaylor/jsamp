package org.astrogrid.samp.hub;

/**
 * Exception thrown when some error occurs in a hub service implementation.
 * These exceptions will be handled at the server end, and should never
 * be seen by users of the client toolkit.  Clients may however see stacktraces
 * corresponding to HubServiceExceptions passed back via the SAMP transport
 * protocol.
 *
 * @author   Mark Taylor
 * @since    24 Jul 2008
 */
public class HubServiceException extends Exception {

    /**
     * Constructs an exception with no message.
     */
    public HubServiceException() {
        super();
    }

    /**
     * Consructs an exception with a given message.
     *
     * @param   msg  message
     */
    public HubServiceException( String msg ) {
        super( msg );
    }

    /**
     * Constructs an exception with a given cause.
     *
     * @param  cause  cause of this exception
     */
    public HubServiceException( Throwable cause ) {
        super( cause );
    }

    /**
     * Constructs an exception with a given message and cause.
     *
     * @param   msg  message
     * @param  cause  cause of this exception
     */
    public HubServiceException( String msg, Throwable cause ) {
        super( msg, cause );
    }
}
