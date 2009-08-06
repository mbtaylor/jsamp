package org.astrogrid.samp.bridge;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.astrogrid.samp.httpd.UtilServer;

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
    private final boolean exportFiles_;
    private static final Logger logger_ =
        Logger.getLogger( UrlExporter.class.getName() );
    private static final Pattern LOCALHOST_REGEX =
        Pattern.compile( "(http://|ftp://)"
                       + "(127\\.0\\.0\\.1|localhost)"
                       + "([:/].*)" );
    private static final Pattern FILE_REGEX =
        Pattern.compile( "(file://)"
                       + "([^/]*)"
                       + "/.*" );

    /**
     * Constructor.
     *
     * @param   host   public name of the host to which loopback addresses
     *                 refer
     * @param   exportFiles  whether to export file-protocol URLs
     *                       by turning them into http ones;
     *                       this only makes sense if the current JVM
     *                       is running on a machine which can see
     *                       <code>host</code>'s filesystem(s)
     */
    public UrlExporter( String host, boolean exportFiles ) {
        host_ = host;
        exportFiles_ = exportFiles;
    }

    /**
     * Exports a single string for remote usage.
     * If it looks like a URL, it's changed.  Not foolproof.
     *
     * @param   text   string to assess
     * @return  copy of text if it's not a URL, otherwise a possibly 
     *          edited URL with the same content
     */
    public String exportString( String text ) {
        String t2 = doExportString( text );
        if ( t2 != null && ! t2.equals( text ) ) {
            logger_.info( "Exported string \"" + text + "\" -> \"" + t2 + '"' );
        }
        return t2;
    }

    /**
     * Does the work for {@link #exportString}.
     *
     * @param   text   string to assess
     * @return  copy of text if it's not a URL, otherwise a URL with a 
     *          possibly edited host part
     */
    private String doExportString( String text ) {
        Matcher localMatcher = LOCALHOST_REGEX.matcher( text );
        if ( localMatcher.matches() ) {
            return localMatcher.group( 1 ) + host_ + localMatcher.group( 3 );
        }
        else if ( exportFiles_ && FILE_REGEX.matcher( text ).matches() ) {
            try {
                URL fileUrl = new URL( text );
                String path = fileUrl.getPath();
                if ( File.separatorChar != '/' ) {
                    path = path.replace( '/', File.separatorChar );
                }
                File file = new File( path );
                if ( file.canRead() && ! file.isDirectory() ) {
                    URL expUrl = UtilServer.getInstance()
                                           .getMapperHandler()
                                           .addLocalUrl( fileUrl );
                    if ( expUrl != null ) { 
                        return expUrl.toString();
                    }
                }
            }
            catch ( MalformedURLException e ) {
                // not a URL at all - don't attempt to export it
            }
            catch ( IOException e ) {
                // something else went wrong - leave alone
            }
            return text;
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
