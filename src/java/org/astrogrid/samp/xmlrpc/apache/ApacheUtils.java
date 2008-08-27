package org.astrogrid.samp.xmlrpc.apache;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Provides utility methods to perform translations between the 
 * data structurs used by the org.apache.xmlrpc classes and those used
 * by JSAMP.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
class ApacheUtils {

    /**
     * Private constructor prevents instantiation.
     */
    private ApacheUtils() {
    }

    /**
     * Converts an object from JSAMP XML-RPC form to Apache XML-RPC form.
     * Basically, this means converting
     * {@link java.util.Map}s to {@link java.util.Hashtable}s and
     * {@link java.util.List}s to {@link java.util.Vector}s.
     *
     * @param  obj  XML-RPC data structure suitable for use within JSAMP
     * @return   XML-RPC data structure suitable for use within Apache
     */
    public static Object toApache( Object obj ) {
        if ( obj instanceof List ) {
            Vector vec = new Vector();
            for ( Iterator it = ((List) obj).iterator(); it.hasNext(); ) {
                vec.add( toApache( it.next() ) );
            }
            return vec;
        }
        else if ( obj instanceof Map ) {
            Hashtable hash = new Hashtable();
            for ( Iterator it = ((Map) obj).entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                hash.put( entry.getKey(), toApache( entry.getValue() ) );
            }
            return hash;
        }
        else if ( obj instanceof String ) {
            return obj;
        }
        else {
            throw new IllegalArgumentException(
                "Non-SAMP object type " + ( obj == null
                                                ? null
                                                : obj.getClass().getName() ) );
        }
    }

    /**
     * Converts an object from Apache XML-RPC form to JSAMP XML-RPC form.
     * Since Hashtable implements Map and Vector implements List, this is
     * a no-op.
     *
     * @param  data XML-RPC data structure suitable for use within Apache
     * @return   XML-RPC data structure suitable for use within JSAMP
     */
    public static Object fromApache( Object data ) {
        return data;
    }
}
