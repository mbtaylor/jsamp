package org.astrogrid.samp.bridge;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exports SAMP data objects which have been created on a given host
 * for use in a remote context.  The job that needs doing is to 
 * convert URLs which reference the host in a way that only makes sense
 * locally (as a loopback address 127.0.0.1 or localhost) to a form
 * which can be used on remote hosts.
 *
 * <p>This is not an exact science; a best effort is made.
 *
 * @author   Mark Taylor
 * @since    29 Jul 2009
 */
class UrlExporter {

    private final String host_;
    private static final Pattern LOCALHOST_REGEX =
        Pattern.compile( "(http://|ftp://)"
                       + "(127\\.0\\.0\\.1|localhost)"
                       + "([:/].*)" );

    /**
     * Constructor.
     *
     * @param   host   public name of the host to which loopback addresses
     *                 refer
     */
    public UrlExporter( String host ) {
        host_ = host;
    }

    /**
     * Exports a single string for remote usage.
     * If it looks like a URL, it's changed.  Not foolproof.
     *
     * @param   text   string to assess
     * @return  copy of text if it's not a URL, otherwise a URL with a 
     *          possibly edited host part
     */
    public String exportString( String text ) {
        Matcher matcher = LOCALHOST_REGEX.matcher( text );
        if ( matcher.matches() ) {
            return matcher.group( 1 ) + host_ + matcher.group( 3 );
        }
        else {
            return text;
        }
    }

    /**
     * Exports a list for remote usage by changing its contents in place.
     *
     * @param  list   list to edit
     */
    public void exportList( List list ) {
        for ( ListIterator it = list.listIterator(); it.hasNext(); ) {
            Object value = it.next();
            if ( value instanceof String ) {
                it.set( exportString( (String) value ) );
            }
            else if ( value instanceof List ) {
                exportList( (List) value );
            }
            else if ( value instanceof Map ) {
                exportMap( (Map) value );
            }
        }
    }

    /**
     * Exports a map for remote usage by changing its contents in place.
     *
     * @param  map   map to edit
     */
    public void exportMap( Map map ) {
        for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Object value = entry.getValue();
            if ( value instanceof String ) {
                entry.setValue( exportString( (String) value ) );
            }
            else if ( value instanceof List ) {
                exportList( (List) value );
            }
            else if ( value instanceof Map ) {
                exportMap( (Map) value );
            }
        }
    }
}
