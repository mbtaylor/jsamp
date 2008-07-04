package org.astrogrid.samp;

import java.util.HashMap;
import java.util.Map;

public class Message extends SampMap {

    public static final String MTYPE_KEY;
    public static final String PARAMS_KEY;
    private static final String[] KNOWN_KEYS = new String[] {
        MTYPE_KEY = "samp.mtype",
        PARAMS_KEY = "samp.params",
    };

    public Message() {
        super( KNOWN_KEYS );
    }

    public Message( Map map ) {
        this();
        putAll( map );
    }

    public Message( String mtype, Map params ) {
        this();
        put( MTYPE_KEY, mtype );
        put( PARAMS_KEY, params == null ? new HashMap() : params );
    }

    public Message( String mtype ) {
        this( mtype, null );
    }

    public String getMType() {
        return getString( MTYPE_KEY );
    }

    public void setParams( Map params ) {
        put( PARAMS_KEY, params );
    }

    public Map getParams() {
        return getMap( PARAMS_KEY );
    }

    public Message addParam( String name, Object value ) {
        if ( ! containsKey( PARAMS_KEY ) ) {
            put( PARAMS_KEY, new HashMap() );
        }
        getParams().put( name, value );
        return this;
    }

    public static Message asMessage( Map map ) {
        return ( map instanceof Message || map == null )
             ? (Message) map
             : new Message( map );
    }
}
