package org.astrogrid.samp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Subscriptions extends SampMap {

    public Subscriptions() {
        super( new String[ 0 ] );
    }

    public Subscriptions( Map map ) {
        this();
        putAll( map );
    }

    public void addMType( String mtype ) {
        put( mtype, new HashMap() );
    }

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

    public static Subscriptions asSubscriptions( Map map ) {
        return ( map instanceof Subscriptions || map == null )
             ? (Subscriptions) map
             : new Subscriptions( map );
    }

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
