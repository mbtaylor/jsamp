package org.astrogrid.samp;

import java.util.Map;

public class RegInfo extends SampMap {

    public static final String HUBID_KEY;
    public static final String SELFID_KEY;
    public static final String PRIVATEKEY_KEY;
    private static final String[] KNOWN_KEYS = new String[] {
        HUBID_KEY = "samp.hub-id",
        SELFID_KEY = "samp.self-id",
        PRIVATEKEY_KEY = "samp.private-key",
    };

    public RegInfo() {
        super( KNOWN_KEYS );
    }

    public RegInfo( Map map ) {
        this();
        putAll( map );
    }

    public RegInfo( String hubId, String selfId, String privateKey ) {
        this();
        put( HUBID_KEY, hubId );
        put( SELFID_KEY, selfId );
        put( PRIVATEKEY_KEY, privateKey );
    }

    public String getHubId() {
        return getString( HUBID_KEY );
    }

    public String getSelfId() {
        return getString( SELFID_KEY );
    }

    public String getPrivateKey() {
        return getString( PRIVATEKEY_KEY );
    }

    public static RegInfo asRegInfo( Map map ) {
        return map instanceof RegInfo
             ? (RegInfo) map
             : new RegInfo( map );
    }
}
