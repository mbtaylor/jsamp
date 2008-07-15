package org.astrogrid.samp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

/**
 * Represents the error information associated with a SAMP response.
 * This corresponds to the <code>samp.error</code> entry in a response map.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class ErrInfo extends SampMap {

    /** Key for short description of what went wrong. */
    public static final String ERRORTXT_KEY = "samp.errortxt";

    /** Key for free-form text given more information about the error. */
    public static final String USERTXT_KEY = "samp.usertxt";

    /** Key for debugging information such as a stack trace. */
    public static final String DEBUGTXT_KEY = "samp.debugtxt";

    /** Key for a numeric or textual code identifying the error. */
    public static final String CODE_KEY = "samp.code";

    private static final String[] KNOWN_KEYS = new String[] {
        ERRORTXT_KEY,
        USERTXT_KEY,
        DEBUGTXT_KEY,
        CODE_KEY,
    };

    /**
     * Constructs an empty ErrInfo.
     */
    public ErrInfo() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs an ErrInfo based on a given Throwable.
     *
     * @param  e  error
     */
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

    /**
     * Constructs an ErrInfo based on an existing map.
     *
     * @param  map   map containing initial data for this object
     */
    public ErrInfo( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs an ErrInfo with a given {@link #ERRORTXT_KEY} value.
     *
     * @param  errortxt  short string describing what went wrong
     */
    public ErrInfo( String errortxt ) {
        this();
        put( ERRORTXT_KEY, errortxt );
    }

    /**
     * Sets the value for the {@link #ERRORTXT_KEY} key.
     *
     * @param   errortxt   short string describing what went wrong
     */
    public void setErrortxt( String errortxt ) {
        put( ERRORTXT_KEY, errortxt );
    }

    /**
     * Returns the value for the {@link #ERRORTXT_KEY} key.
     *
     * @return  short string describing what went wrong
     */
    public String getErrortxt() {
        return getString( ERRORTXT_KEY );
    }

    /**
     * Sets the value for the {@link #USERTXT_KEY} key.
     *
     * @param  usertxt  free-form string giving more detail on the error
     */
    public void setUsertxt( String usertxt ) {
        put( USERTXT_KEY, usertxt );
    }

    /**
     * Returns the value for the {@link #USERTXT_KEY} key.
     *
     * @return  free-form string giving more detail on the error
     */
    public String getUsertxt() {
        return getString( USERTXT_KEY );
    }

    /**
     * Sets the value for the {@link #DEBUGTXT_KEY} key.
     *
     * @param  debugtxt  string containing debugging information, such as a
     *                   a stack trace
     */
    public void setDebugtxt( String debugtxt ) {
        put( DEBUGTXT_KEY, debugtxt );
    }

    /**
     * Returns the value for the {@link #DEBUGTXT_KEY} key.
     *
     * @return  string containing debugging information, such as a stack trace
     */
    public String getDebugtxt() {
        return getString( DEBUGTXT_KEY );
    }

    /**
     * Sets the value for the {@link #CODE_KEY} key.
     *
     * @param  code  numeric or textual code identifying the error
     */
    public void setCode( String code ) {
        put( CODE_KEY, code );
    }

    /**
     * Returns the value for the {@link #CODE_KEY} key.
     *
     * @return  numeric or textual code identifying the error
     */
    public String getCode() {
        return getString( CODE_KEY );
    }

    public void check() throws DataException {
        super.check();
        checkHasKeys( new String[] { ERRORTXT_KEY, } );
    }

    /**
     * Returns a given map as an ErrInfo object.
     *
     * @param  map  map
     * @return   errInfo
     */
    public static ErrInfo asErrInfo( Map map ) {
        return ( map instanceof ErrInfo || map == null )
             ? (ErrInfo) map
             : new ErrInfo( map );
    }

    /**
     * Generates a string containing a stack trace for a given throwable.
     *
     * @param   e   error
     * @return  stacktrace
     */
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
