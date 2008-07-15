package org.astrogrid.samp;

/**
 * Unchecked exception thrown when a data structure for use with 
 * SAMP does not have the correct form.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class DataException extends IllegalArgumentException {

    /**
     * Constructs an exception with no message.
     */
    public DataException() {
        super();
    }

    /**
     * Consructs an exception with a given message.
     *
     * @param   msg  message
     */
    public DataException( String msg ) {
        super( msg );
    }

    /**
     * Constructs an exception with a given cause.
     *
     * @param  e  cause of this exception
     */
    public DataException( Throwable e ) {
        this();
        initCause( e );
    }

    /**
     * Constructs an exception with a given message and cause.
     *
     * @param   msg  message
     * @param  e  cause of this exception
     */
    public DataException( String msg, Throwable e ) {
        this( msg );
        initCause( e );
    }
}
