package org.astrogrid.samp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the set of subscribed messages for a SAMP client.
 * This has the form of a Map in which each key is an MType (perhaps
 * wildcarded) and the corresponding values are maps with keys which are
 * so far undefined (thus typically empty).
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class Subscriptions extends SampMap {

    private static final String ATOM_REGEX = "[0-9a-z\\-_]+";
    private static String MTYPE_REGEX =
        "(" + ATOM_REGEX + "\\.)*" + ATOM_REGEX;
    private static String MSUB_REGEX =
        "(" + MTYPE_REGEX + "|" + MTYPE_REGEX + "\\.\\*" + "|" + "\\*" + ")";
    private static final Pattern MSUB_PATTERN = Pattern.compile( MSUB_REGEX );

    /**
     * Constructs an empty subscriptions object.
     */
    public Subscriptions() {
        super( new String[ 0 ] );
    }

    /**
     * Constructs a subscriptions object based on an existing map.
     *
     * @param  map  map containing initial data for this object
     */
    public Subscriptions( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Adds a subscription to a given MType.  <code>mtype</code> may include
     * a wildcard according to the SAMP rules.
     *
     * @param  mtype   subscribed MType, possibly wildcarded
     */
    public void addMType( String mtype ) {
        put( mtype, new HashMap() );
    }

    /**
     * Determines whether a given (non-wildcarded) MType is subscribed to
     * by this object.
     *
     * @param  mtype  MType to test
     */
    public boolean isSubscribed( String mtype ) {
        if ( containsKey( mtype ) ) {
            return true;
        }
        for ( Iterator it = keySet().iterator(); it.hasNext(); ) {
            if ( matchLevel( (String) it.next(), mtype ) >= 0 ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the map which forms the value for a given MType key.
     * If a wildcarded subscription is recorded which matches
     * <code>mtype</code>, the corresponding value is returned.
     * If <code>mtype</code> is not subscribed to, <code>null</code>
     * is returned.
     *
     * @param   mtype  MType to query
     * @return  map value corresponding to <code>mtype</code>, or null
     */
    public Map getSubscription( String mtype ) {
        if ( containsKey( mtype ) ) {
            Object value = get( mtype );
            return value instanceof Map ? (Map) value
                                        : (Map) new HashMap();
        }
        else {
            int bestLevel = -1;
            Map bestValue = null;
            for ( Iterator it = entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                int level = matchLevel( (String) entry.getKey(), mtype );
                if ( level > bestLevel ) {
                    bestLevel = level;
                    Object value = entry.getValue();
		    bestValue = value instanceof Map ? (Map) value
                                                     : (Map) new HashMap();
                }
            }
            return bestValue;
        }
    }

    public void check() {
        super.check();
        for ( Iterator it = entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            if ( ! MSUB_PATTERN.matcher( key ).matches() ) {
                throw new DataException( "Illegal subscription key " + key );
            }
            if ( ! ( value instanceof Map ) ) {
                throw new DataException( "Subscription values "
                                       + "are not all maps" );
            }
        }
    }

    /**
     * Returns a given map in the form of a Subscriptions object.
     *
     * @param  map  map
     * @return  subscriptions
     */
    public static Subscriptions asSubscriptions( Map map ) {
        return ( map instanceof Subscriptions || map == null )
             ? (Subscriptions) map
             : new Subscriptions( map );
    }

    /**
     * Performs wildcard matching of MTypes.  The result is the number of
     * dot-separated "atoms" which match between the two.
     *
     * @param  pattern   MType pattern; may contain a wildcard
     * @param  mtype   unwildcarded MType for comparison with 
     *         <code>pattern</code>
     * @return  the number of atoms of <code>pattern</code> which match 
     *          <code>mtype</code>; if <code>pattern</code>="*" the result is
     *          0, and if there is no match the result is -1
     */
    public static int matchLevel( String pattern, String mtype ) {
        if ( mtype.equals( pattern ) ) {
            return countAtoms( pattern );
        }
        else if ( "*".equals( pattern ) ) {
            return 0;
        }
        else if ( pattern.endsWith( ".*" ) ) {
            String prefix = pattern.substring( 0, pattern.length() - 2 );
            return mtype.startsWith( prefix ) ? countAtoms( prefix )
                                              : -1;
        }
        else {
            return -1;
        }
    }

    /**
     * Counts the number of dot-separated "atoms" in a string.
     *
     * @param  text  string to test
     */
    private static int countAtoms( String text ) {
        int leng = text.length();
        int natom = 1;
        for ( int i = 0; i < leng; i++ ) {
            if ( text.charAt( i ) == '.' ) {
                natom++;
            }
        }
        return natom;
    }
}
