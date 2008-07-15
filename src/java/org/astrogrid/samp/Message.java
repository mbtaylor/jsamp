package org.astrogrid.samp;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an encoded SAMP Message.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class Message extends SampMap {

    /** Key for message MType. */
    public static final String MTYPE_KEY = "samp.mtype";

    /** Key for map of parameters used by this message. */
    public static final String PARAMS_KEY = "samp.params";

    private static final String[] KNOWN_KEYS = new String[] {
        MTYPE_KEY,
        PARAMS_KEY,
    };

    /**
     * Constructs an empty message.
     */
    public Message() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a message based on an existing map.
     *
     * @param  map  map containing initial data for this object
     */
    public Message( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs a message with a given MType and params map.
     *
     * @param  mtype  value for {@link #MTYPE_KEY} key
     * @param  params value for {@link #PARAMS_KEY} key
     */
    public Message( String mtype, Map params ) {
        this();
        put( MTYPE_KEY, mtype );
        put( PARAMS_KEY, params == null ? new HashMap() : params );
    }

    /**
     * Constructs a message with a given MType.
     * The parameters map will be mutable.
     *
     * @param  mtype  value for {@link #MTYPE_KEY} key
     */
    public Message( String mtype ) {
        this( mtype, null );
    }

    /**
     * Returns this message's MType.
     *
     * @return  value for {@link #MTYPE_KEY}
     */
    public String getMType() {
        return getString( MTYPE_KEY );
    }

    /**
     * Sets this message's params map.
     *
     * @param  params  value for {@link #PARAMS_KEY}
     */
    public void setParams( Map params ) {
        put( PARAMS_KEY, params );
    }

    /**
     * Returns this message's params map.
     *
     * @return  value for {@link #PARAMS_KEY}
     */
    public Map getParams() {
        return getMap( PARAMS_KEY );
    }

    /**
     * Sets the value for a single entry in this message's 
     * <code>samp.params</code> map.
     *
     * @param  name  param name
     * @param  value  param value
     */
    public Message addParam( String name, Object value ) {
        if ( ! containsKey( PARAMS_KEY ) ) {
            put( PARAMS_KEY, new HashMap() );
        }
        getParams().put( name, value );
        return this;
    }

    public void check() throws DataException {
        super.check();
        checkHasKeys( new String[] { MTYPE_KEY } );
    }

    /**
     * Returns a given map as a Message object.
     *
     * @param  map  map
     * @return  message
     */ 
    public static Message asMessage( Map map ) {
        return ( map instanceof Message || map == null )
             ? (Message) map
             : new Message( map );
    }
}
