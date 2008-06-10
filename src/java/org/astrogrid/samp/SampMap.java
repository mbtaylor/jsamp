package org.astrogrid.samp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SampMap extends HashMap {

    public SampMap() {
        super();
    }

    public SampMap( Map map ) {
        super( map );
    }

    public void check() throws DataException {
        SampUtils.checkMap( this );
    }

    public String getString( String key ) {
        return (String) get( key );
    }

    public Map getMap( String key ) {
        return (Map) get( key );
    }

    public List getList( String key ) {
        return (List) get( key );
    }

    public URL getUrl( String key ) {
        String loc = getString( key );
        if ( loc == null ) {
            return null;
        }
        else {
            try {
                return new URL( loc );
            }
            catch ( MalformedURLException e ) {
                return null;
            }
        }
    }

    public static SampMap asSampMap( Map map ) {
        return ( map instanceof SampMap || map == null )
             ? (SampMap) map
             : new SampMap( map );
    }
}
