package org.astrogrid.samp;

import java.io.IOException;

public class SampException extends IOException {

    public SampException() {
        super();
    }

    public SampException( String msg ) {
        super( msg );
    }

    public SampException( Throwable cause ) {
        this();
        initCause( cause );
    }

    public SampException( String msg, Throwable cause ) {
        this( msg );
        initCause( cause );
    }
}
