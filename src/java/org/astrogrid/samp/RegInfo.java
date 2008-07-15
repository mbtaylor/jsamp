package org.astrogrid.samp;

import java.util.Map;

/**
 * Represents information provided to a client at registration by the hub.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class RegInfo extends SampMap {

    /** Key for client public-id used by hub when sending messages itself. */
    public static final String HUBID_KEY = "samp.hub-id";

    /** Key for client public-id owned by the registering application. */
    public static final String SELFID_KEY = "samp.self-id";

    /** Key for private-key token used for communications between hub and
     *  registering client (Standard Profile). */
    public static final String PRIVATEKEY_KEY = "samp.private-key";

    private static final String[] KNOWN_KEYS = new String[] {
        HUBID_KEY,
        SELFID_KEY,
        PRIVATEKEY_KEY,
    };

    /**
     * Constructs an empty RegInfo.
     */
    public RegInfo() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a RegInfo based on an existing map.
     *
     * @param  map  map containing initial data for this object
     */
    public RegInfo( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs a RegInfo with all essential information.
     *
     * @param  hubId  {@link #HUBID_KEY} value
     * @param  selfId {@link #SELFID_KEY} value
     * @param  privateKey {@link #PRIVATEKEY_KEY} value
     */
    public RegInfo( String hubId, String selfId, String privateKey ) {
        this();
        put( HUBID_KEY, hubId );
        put( SELFID_KEY, selfId );
        put( PRIVATEKEY_KEY, privateKey );
    }

    /**
     * Returns the hub's own public client id.
     *
     * @return  {@link #HUBID_KEY} value
     */
    public String getHubId() {
        return getString( HUBID_KEY );
    }

    /**
     * Returns the registered client's public client id.
     *
     * @return  {@link #SELFID_KEY} value
     */
    public String getSelfId() {
        return getString( SELFID_KEY );
    }

    /**
     * Returns the registered client's private key (Standard Profile).
     *
     * @return  {@link #PRIVATEKEY_KEY} value
     */
    public String getPrivateKey() {
        return getString( PRIVATEKEY_KEY );
    }

    public void check() {
        super.check();
        checkHasKeys( new String[] { HUBID_KEY, SELFID_KEY, } );
    }

    /**
     * Returns a given map as a RegInfo.
     *
     * @param  map  map 
     * @return  registration info
     */
    public static RegInfo asRegInfo( Map map ) {
        return map instanceof RegInfo
             ? (RegInfo) map
             : new RegInfo( map );
    }
}
