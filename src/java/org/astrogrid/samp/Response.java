package org.astrogrid.samp;

import java.util.Map;

public class Response extends SampMap {

    public static final String STATUS_KEY = "samp.status";
    public static final String RESULT_KEY = "samp.result";
    public static final String ERROR_KEY = "samp.error";

    public static final String OK_STATUS = "samp.ok";
    public static final String WARNING_STATUS = "samp.warning";
    public static final String ERROR_STATUS = "samp.error";

    public Response( String status, Map result, ErrInfo errinfo ) {
        put( STATUS_KEY, status );
        if ( result != null ) {
            put( RESULT_KEY, result );
        }
        if ( errinfo != null ) {
            put( ERROR_KEY, errinfo );
        }
    }

    public Response() {
        super();
    }

    public Response( Map map ) {
        super( map );
    }

    public void setStatus( String status ) {
        put( STATUS_KEY, status );
    }

    public String getStatus() {
        return getString( STATUS_KEY );
    }

    public void setResult( Map result ) {
        put( RESULT_KEY, result );
    }

    public Map getResult() {
        return getMap( RESULT_KEY );
    }

    public void setErrInfo( Map errInfo ) {
        put( ERROR_KEY, errInfo );
    }

    public ErrInfo getErrInfo() {
        return ErrInfo.asErrInfo( getMap( ERROR_KEY ) );
    }

    public boolean isOK() {
        return OK_STATUS.equals( get( STATUS_KEY ) );
    }

    public static Response asResponse( Map map ) {
        return ( map instanceof Response || map == null )
             ? (Response) map
             : new Response( map );
    }

    public static Response createSuccessResponse( Map result ) {
        return new Response( OK_STATUS, result, null );
    }

    public static Response createErrorResponse( ErrInfo errinfo ) {
        return new Response( ERROR_STATUS, null, errinfo );
    }
}
