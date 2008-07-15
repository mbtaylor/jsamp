package org.astrogrid.samp;

import java.util.Map;

/**
 * Represents an encoded SAMP response.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class Response extends SampMap {

    /** Key for response status.  May take one of the values
     *  {@link #OK_STATUS}, {@link #WARNING_STATUS} or {@link #ERROR_STATUS}.
     */
    public static final String STATUS_KEY = "samp.status";

    /** Key for result map.  This is a map of key-value pairs with semantics
     *  defined by the original message's MType.
     *  Only present in case of success (or warning). */
    public static final String RESULT_KEY = "samp.result";

    /** Key for error map.  Only present in case of failure (or warning). */
    public static final String ERROR_KEY = "samp.error";

    private static final String[] KNOWN_KEYS = new String[] {
        STATUS_KEY,
        RESULT_KEY,
        ERROR_KEY,
    };

    /** {@link #STATUS_KEY} value indicating success. */
    public static final String OK_STATUS = "samp.ok";

    /** {@link #STATUS_KEY} value indicating partial success. */
    public static final String WARNING_STATUS = "samp.warning";

    /** {@link #STATUS_KEY} value indicating failure. */
    public static final String ERROR_STATUS = "samp.error";

    /**
     * Constructs an empty response.
     */
    public Response() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a response based on an existing map.
     *
     * @param   map  map containing initial data for this object
     */
    public Response( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs a response with given status, result and error.
     *
     * @param  status  {@link #STATUS_KEY} value
     * @param  result  {@link #RESULT_KEY} value
     * @param  errinfo {@link #ERROR_KEY} value
     */
    public Response( String status, Map result, ErrInfo errinfo ) {
        this();
        put( STATUS_KEY, status );
        if ( result != null ) {
            put( RESULT_KEY, result );
        }
        if ( errinfo != null ) {
            put( ERROR_KEY, errinfo );
        }
    }

    /**
     * Sets the status value.
     *
     * @param  status  {@link #STATUS_KEY} value
     */
    public void setStatus( String status ) {
        put( STATUS_KEY, status );
    }

    /**
     * Returns the status value.
     *
     * @return  {@link #STATUS_KEY} value 
     */
    public String getStatus() {
        return getString( STATUS_KEY );
    }

    /**
     * Sets the result map.
     *
     * @param  result  {@link #RESULT_KEY} value
     */
    public void setResult( Map result ) {
        put( RESULT_KEY, result );
    }

    /**
     * Returns the result map.
     *
     * @return   {@link #RESULT_KEY} value
     */
    public Map getResult() {
        return getMap( RESULT_KEY );
    }

    /**
     * Sets the error object.
     *
     * @param  errInfo  {@link #ERROR_KEY} value
     * @see  ErrInfo
     */
    public void setErrInfo( Map errInfo ) {
        put( ERROR_KEY, errInfo );
    }

    /**
     * Returns the error object.
     *
     * @return   {@link #ERROR_KEY} value as an <code>ErrInfo</code>
     */
    public ErrInfo getErrInfo() {
        return ErrInfo.asErrInfo( getMap( ERROR_KEY ) );
    }

    /**
     * Indicates whether the result was an unequivocal success.
     *
     * @return   true iff <code>getStatus()==OK_STATUS</code>
     */
    public boolean isOK() {
        return OK_STATUS.equals( get( STATUS_KEY ) );
    }

    public void check() {
        super.check();
        checkHasKeys( new String[] { STATUS_KEY, } );
        String status = getStatus();
        if ( OK_STATUS.equals( status ) || WARNING_STATUS.equals( status ) ) {
            if ( ! containsKey( RESULT_KEY ) ) {
                throw new DataException( STATUS_KEY + "=" + status
                                       + " but no " + RESULT_KEY );
            }
        }
        if ( ERROR_STATUS.equals( status ) ||
             WARNING_STATUS.equals( status ) ) {
            if ( ! containsKey( ERROR_KEY ) ) {
                throw new DataException( STATUS_KEY + "=" + status
                                       + " but not " + ERROR_KEY );
            }
        }
        if ( ! containsKey( RESULT_KEY ) && ! containsKey( ERROR_KEY ) ) {
            throw new DataException( "Neither " + RESULT_KEY + 
                                     " nor " + ERROR_KEY +
                                     " keys present" );
        }
        if ( containsKey( ERROR_KEY ) ) {
            ErrInfo.asErrInfo( getMap( ERROR_KEY ) ).check();
        }
    }

    /**
     * Returns a new response which is a success.
     *
     * @param  result  key-value map representing results of successful call
     * @return  new success response
     */
    public static Response createSuccessResponse( Map result ) {
        return new Response( OK_STATUS, result, null );
    }

    /**
     * Returns a new response which is an error.
     *
     * @param  errinfo   error information
     * @return  new error response
     */
    public static Response createErrorResponse( ErrInfo errinfo ) {
        return new Response( ERROR_STATUS, null, errinfo );
    }

    /**
     * Returns a map as a Response object.
     *
     * @param  map  map
     * @return   response
     */
    public static Response asResponse( Map map ) {
        return ( map instanceof Response || map == null )
             ? (Response) map
             : new Response( map );
    }
}
