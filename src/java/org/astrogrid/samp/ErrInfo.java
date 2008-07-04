package org.astrogrid.samp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

public class ErrInfo extends SampMap {

    public static final String ERRORTXT_KEY;
    public static final String USERTXT_KEY;
    public static final String DEBUGTXT_KEY;
    public static final String CODE_KEY;
    private static final String[] KNOWN_KEYS = new String[] {
        ERRORTXT_KEY = "samp.errortxt",
        USERTXT_KEY = "samp.usertxt",
        DEBUGTXT_KEY = "samp.debugtxt",
        CODE_KEY = "samp.code",
    };

    public ErrInfo() {
        super( KNOWN_KEYS );
    }

    public ErrInfo( Throwable e ) {
        this();
        String txt = e.getMessage();
        if ( txt == null || txt.trim().length() == 0 ) {
            txt = e.getClass().getName();
        }
        put( ERRORTXT_KEY, txt );
        put( USERTXT_KEY, e.toString() );
        put( DEBUGTXT_KEY, getStackTrace( e ) );
        put( CODE_KEY, e.getClass().getName() );
    }

    public ErrInfo( Map map ) {
        this();
        putAll( map );
    }

    public ErrInfo( String errortxt ) {
        this();
        put( ERRORTXT_KEY, errortxt );
    }

    public void setErrortxt( String errortxt ) {
        put( ERRORTXT_KEY, errortxt );
    }

    public String getErrortxt() {
        return getString( ERRORTXT_KEY );
    }

    public void setUsertxt( String usertxt ) {
        put( USERTXT_KEY, usertxt );
    }

    public String getUsertxt() {
        return getString( USERTXT_KEY );
    }

    public void setDebugtxt( String debugtxt ) {
        put( DEBUGTXT_KEY, debugtxt );
    }

    public String getDebugtxt() {
        return getString( DEBUGTXT_KEY );
    }

    public void setCode( String code ) {
        put( CODE_KEY, code );
    }

    public String getCode() {
        return getString( CODE_KEY );
    }

    public void check() throws DataException {
        super.check();
        if ( ! containsKey( ERRORTXT_KEY ) ) {
            throw new DataException( "Required entry key "
                                   + ERRORTXT_KEY + " is missing" );
        }
    }

    public static ErrInfo asErrInfo( Map map ) {
        return ( map instanceof ErrInfo || map == null )
             ? (ErrInfo) map
             : new ErrInfo( map );
    }

    private static String getStackTrace( Throwable e ) {
        byte[] bbuf;
        try {
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            e.printStackTrace( new PrintStream( bOut ) );
            bOut.close();
            bbuf = bOut.toByteArray();
        }
        catch ( IOException ioex ) {
            assert false;
            return "error generating stacktrace";
        }
        StringBuffer sbuf = new StringBuffer( bbuf.length );
        for ( int ic = 0; ic < bbuf.length; ic++ ) {
            char c = (char) bbuf[ ic ];
            if ( c >= 0x20 && c <= 0x7e ) {
                sbuf.append( c );
            }
        }
        return sbuf.toString();
    }
}
