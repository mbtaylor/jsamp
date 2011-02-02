package org.astrogrid.samp.web;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampMap;
import org.astrogrid.samp.SampUtils;

/**
 * Map representing a client callback from the hub.
 * It normally contains a callback method name and a list of parameters.
 * An instance of this class can be used to correspond to one of the calls
 * in the {@link org.astrogrid.samp.client.CallableClient} interface.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
class Callback extends SampMap {

    /** Key for the callback method name (a string). */
    public static final String METHODNAME_KEY = "samp.methodName";

    /** Key for the callback parameters (a list). */
    public static final String PARAMS_KEY = "samp.params";

    private static final String[] KNOWN_KEYS = new String[] {
        METHODNAME_KEY,
        PARAMS_KEY,
    };

    /**
     * Constructs an empty callback.
     */
    public Callback() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a callback based on an existing map.
     *
     * @param   map  contents
     */
    public Callback( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs a callback given a method name and parameter list.
     */
    public Callback( String methodName, List params ) {
        this();
        setMethodName( methodName );
        setParams( params );
    }

    /**
     * Sets the method name.
     *
     * @param  methodName  method name
     */
    public void setMethodName( String methodName ) {
        put( METHODNAME_KEY, methodName );
    }

    /**
     * Returns the method name.
     *
     * @return  method name
     */
    public String getMethodName() {
        return getString( METHODNAME_KEY );
    }

    /**
     * Sets the parameter list.
     *
     * @param  params  parameter list
     */
    public void setParams( List params ) {
        SampUtils.checkList( params );
        put( PARAMS_KEY, params );
    }

    /**
     * Returns the parameter list.
     *
     * @return  parameter list
     */
    public List getParams() {
        return getList( PARAMS_KEY );
    }

    public void check() {
        super.check();
        checkHasKeys( new String[] { METHODNAME_KEY, PARAMS_KEY, } );
        SampUtils.checkString( getMethodName() );
        SampUtils.checkList( getParams() );
    }

    /**
     * Returns a given map as a Callback object.
     *
     * @param  map  map
     * @return callback
     */
    public static Callback asCallback( Map map ) {
        return ( map instanceof Callback || map == null )
             ? (Callback) map
             : new Callback( map );
    }
}
