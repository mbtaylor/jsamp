package org.astrogrid.samp;

import java.util.Map;

public class ErrInfo extends SampMap {

    public static final String ERRORTXT_KEY = "samp.errortxt";
    public static final String USERTXT_KEY = "samp.usertxt";
    public static final String DEBUGTXT_KEY = "samp.debugtxt";
    public static final String CODE_KEY = "samp.code";

    public ErrInfo() {
        super();
    }

    public ErrInfo( Map map ) {
        super( map );
    }

    public ErrInfo( String errortxt ) {
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
}
