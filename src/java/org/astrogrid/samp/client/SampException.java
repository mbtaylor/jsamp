package org.astrogrid.samp.client;

import java.io.IOException;

/**
 * Exception thrown when some error occurs in SAMP processing.
 * Note that this is a subclass of {@link java.io.IOException}.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class SampException extends IOException {

    /**
     * Constructs an exception with no message.
     */
    public SampException() {
        super();
    }

    /**
     * Consructs an exception with a given message.
     *
     * @param   msg  message
     */
    public SampException( String msg ) {
        super( msg );
    }

    /**
     * Constructs an exception with a given cause.
     *
     * @param  cause  cause of this exception
     */
    public SampException( Throwable cause ) {
        this();
        initCause( cause );
    }

    /**
     * Constructs an exception with a given message and cause.
     *
     * @param   msg  message
     * @param  cause  cause of this exception
     */
    public SampException( String msg, Throwable cause ) {
        this( msg );
        initCause( cause );
    }
}
