package org.astrogrid.samp;

public class RegInfo extends SampMap {

    public static final String HUBID_KEY = "samp.hub-id";
    public static final String SELFID_KEY = "samp.self-id";
    public static final String PRIVATEKEY_KEY = "samp.private-key";

    public RegInfo( String hubId, String selfId, String privateKey ) {
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
}
