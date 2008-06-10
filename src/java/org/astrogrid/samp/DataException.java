package org.astrogrid.samp;

public class DataException extends IllegalArgumentException {

    public DataException() {
        super();
    }

    public DataException( String msg ) {
        super( msg );
    }

    public DataException( Throwable e ) {
        this();
        initCause( e );
    }

    public DataException( String msg, Throwable e ) {
        this( msg );
        initCause( e );
    }
}
