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

/**
 * Abstract superclass for objects represented within the SAMP package as
 * key-value maps.  There are several of these, represented by subclasses
 * of <code>SampMap</code>, for instance {@link Message}, {@link Metadata} etc.
 * A <code>SampMap</code> is-a {@link java.util.Map}, but has some 
 * additional useful features:
 * <ul>
 * <li>its entries are ordered in a convenient fashion
 * <li>it contains some general-purpose utility methods appropriate to SAMP maps
 * <li>particular subclasses contain specific constants and utility methods,
 *     e.g. well-known keys and accessor methods for them
 * <li>concrete subclasses provide a static <code>as<em>Class</em></code>
 *     method to convert from a normal Map to the class in question
 * </ul>
 *
 * <p>In general 
 * any time a map-encoded object is required by a method in the toolkit,
 * any <code>Map</code> can be used.  When the toolkit provides a map-encoded
 * object however (as return value or callback method parameter), an object
 * of the more specific <code>SampMap</code> type is used.
 * This allows maximum convenience for the application programmer, but
 * means that you don't have to use these additional features if you 
 * don't want to, you can treat everything as a plain old <code>Map</code>.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public abstract class SampMap extends AbstractMap {

    private final Map baseMap_;
    public static final Map EMPTY =
        Collections.unmodifiableMap( new HashMap() );

    /**
     * Constructor.
     * The given array of well-known keys will appear first in the list of
     * entries when this map is iterated over.  Other entries will appear in
     * alphabetical order.
     *
     * @param  knownKeys  array of well-known keys for this class
     */
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

    /**
     * Checks that this object is ready for use with the SAMP toolkit.
     * As well as calling {@link SampUtils#checkMap} (ensuring that all keys
     * are Strings, and all values Strings, Lists or Maps), subclass-specific
     * invariants may be checked.  In the case that there's something wrong,
     * an informative <code>DataException</code> will be thrown.
     *
     * @throws   DataException  if this object's current state 
     *           is not suitable for SAMP use
     */
    public void check() {
        SampUtils.checkMap( this );
    }

    /**
     * Checks that this map contains at least the given set of keys.
     * If any is absent, an informative <code>DataException</code> will be
     * thrown.  Normally called by {@link #check}.
     *
     * @param  keys   array of required keys for this map
     * @param  throws DataException  if this object does not contain entries
     *         for all elements of the array <code>keys</code>
     */
    public void checkHasKeys( String[] keys ) {
        for ( int i = 0; i < keys.length; i++ ) {
            String key = keys[ i ];
            if ( ! containsKey( key ) ) {
                throw new DataException( "Required key " + key
                                       + " not present" );
            }
        }
    }

    /**
     * Returns the value for a given key in this map, cast to String.
     *
     * @return  string value for <code>key</code>
     */
    public String getString( String key ) {
        return (String) get( key );
    }

    /**
     * returns the value for a given key in this map, cast to Map.
     *
     * @return  map value for <code>key</code>
     */
    public Map getMap( String key ) {
        return (Map) get( key );
    }

    /**
     * Returns the value for a given key in this map, cast to List.
     * 
     * @return list value for <code>key</code>
     */
    public List getList( String key ) {
        return (List) get( key );
    }

    /**
     * Returns the value for a given key in this map as a URL.
     *
     * @return  URL value for <code>key</code>
     */
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
