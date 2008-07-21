package org.astrogrid.samp.test;

/**
 * Exception thrown by a failed test.
 *
 * @author   Mark Taylor
 * @since    18 Jul 2008
 */
public class TestException extends RuntimeException {

    public TestException() {
        super();
    }

    public TestException( String msg ) {
        super( msg );
    }

    public TestException( String msg, Throwable cause ) {
        super( msg, cause );
    }

    public TestException( Throwable cause ) {
        super( cause );
    }
}
