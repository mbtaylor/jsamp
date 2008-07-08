package org.astrogrid.samp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public abstract class SampMap extends AbstractMap {

    private final Map baseMap_;
    public static final Map EMPTY =
        Collections.unmodifiableMap( new HashMap() );

    protected SampMap( String[] knownKeys ) {
        super();
        final List knownKeyList = Arrays.asList( (String[]) knownKeys.clone() );
        baseMap_ = new TreeMap( new Comparator() {
            public int compare( Object o1, Object o2 ) {
                String s1 = o1.toString();
                String s2 = o2.toString();
                int k1 = knownKeyList.indexOf( s1 );
                int k2 = knownKeyList.indexOf( s2 );
                if ( k1 >= 0 ) {
                    if ( k2 >= 0 ) {
                        return k1 - k2;
                    }
                    else {
                        return -1;
                    }
                }
                else if ( k2 >= 0 ) {
                    assert k1 < 0;
                    return +1;
                }
                boolean f1 = s1.startsWith( "samp." );
                boolean f2 = s2.startsWith( "samp." );
                if ( f1 && ! f2 ) {
                    return -1;
                }
                else if ( ! f1 && f2 ) {
                    return +1;
                }
                else {
                    return s1.compareTo( s2 );
                }
            }
        } );
    }

    public Object put( Object key, Object value ) {
        return baseMap_.put( key, value );
    }

    public Set entrySet() {
        return baseMap_.entrySet();
    }

    public void check() throws DataException {
        SampUtils.checkMap( this );
    }

    public void checkHasKeys( String[] keys ) throws DataException {
        for ( int i = 0; i < keys.length; i++ ) {
            String key = keys[ i ];
            if ( ! containsKey( key ) ) {
                throw new DataException( "Required key " + key
                                       + " not present" );
            }
        }
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
}
