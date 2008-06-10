package org.astrogrid.samp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SampUtils {

    public static final String LOCKFILE_NAME = ".samp";

    /**
     * True if spurious java.util.NoSuchElementExceptions should be flagged.
     * If future JVMs fix this bug, this should be set false or the code
     * removed.
     */
    public static boolean WARN_ABOUT_NOSUCHELEMENTEXCEPTIONS = true;

    private SampUtils() {
    }

    public static String encodeInt( int i ) {
        return Integer.toString( i );
    }

    public static int decodeInt( String s ) {
        return Integer.parseInt( s );
    }

    public static String encodeFloat( double d ) {
        if ( Double.isInfinite( d ) ) {
            throw new IllegalArgumentException( "Infinite value "
                                              + "not permitted" );
        }
        if ( Double.isNaN( d ) ) {
            throw new IllegalArgumentException( "NaN not permitted" );
        }
        return Double.toString( d );
    }

    public static double decodeFloat( String s ) {
        return Double.parseDouble( s );
    }

    public static String encodeBoolean( boolean b ) {
        return encodeInt( b ? 1 : 0 );
    }

    public static boolean decodeBoolean( String s ) {
        try {
            return decodeInt( s ) != 0;
        }
        catch ( NumberFormatException e ) {
            return false;
        }
    }

    public static void checkObject( Object obj ) throws DataException {
        if ( obj instanceof Map ) {
            checkMap( (Map) obj );
        }
        else if ( obj instanceof List ) {
            checkList( (List) obj );
        }
        else if ( obj instanceof String ) {
            checkString( (String) obj );
        }
        else if ( obj == null ) {
            throw new DataException( "Bad SAMP object: contains a null" );
        }
        else {
            throw new DataException( "Bad SAMP object: contains a "
                                   + obj.getClass().getName() );
        }
    }

    public static void checkMap( Map map ) throws DataException {
        for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            checkString( (String) entry.getKey() );
            checkObject( entry.getValue() );
        }
    }

    public static void checkList( List list ) throws DataException {
        for ( Iterator it = list.iterator(); it.hasNext(); ) {
            checkObject( it.next() );
        }
    }

    public static void checkString( String string ) throws DataException {
        int leng = string.length();
        for ( int i = 0; i < leng; i++ ) {
            int c = string.charAt( i );
            if ( c < 0x01 || c > 0x7f ) {
                throw new DataException( "Bad SAMP string; contains character "
                                       + "0x" + Integer.toHexString( c ) );
            }
        }
    }

    public static void checkUrl( String url ) throws DataException {
        if ( url != null ) {
            try {
                new URL( (String) url );
            }
            catch ( MalformedURLException e ) {
                throw new DataException( "Bad URL " + url, e );
            }
        }
    }

    public static File getLockFile() {
        return new File( getHomeDir(), LOCKFILE_NAME );
    }

    /**
     * Returns an unused port number on the local host.
     *
     * @param   startPort  suggested port number; ports nearby will be
     *          chosen if this is in use
     * @return  unused port
     */
    public static int getUnusedPort( int startPort ) throws IOException {
        final int nTry = 20;
        for ( int iPort = startPort; iPort < startPort + nTry; iPort++ ) {
            try {
                Socket trySocket = new Socket( "localhost", iPort );
                if ( ! trySocket.isClosed() ) {

                    // This line causes "java.util.NoSuchElementException" to
                    // be written to System.err, at least at J2SE1.4.
                    // Not my fault!
                    if ( WARN_ABOUT_NOSUCHELEMENTEXCEPTIONS ) {
                        WARN_ABOUT_NOSUCHELEMENTEXCEPTIONS = false;
                        System.err.println(
                            "Please ignore spurious \""
                          + "java.util.NoSuchElementException\" messages." );
                    }
                    trySocket.close();
                }
            }
            catch ( ConnectException e ) {

                /* Can't connect - this hopefully means that the socket is
                 * unused. */
                return iPort;
            }
        }
        throw new IOException( "Can't locate an unused port in range " +
                               startPort + " ... " + ( startPort + nTry ) );
    }


    private static File getHomeDir() {
        if ( ! isWindows() ) {
            return new File( System.getProperty( "user.home" ) );
        }
        else {
            String userprofile = null;
            try {
                userprofile = System.getenv( "USERPROFILE" );
            }

            // System.getenv is unimplemented at 1.4, and throws an Error.
            catch ( Throwable e ) {
                try {
                    Process proc = Runtime.getRuntime().exec( new String[] {
                        "cmd", "/c", "echo", "%USERPROFILE%",
                    } );
                    proc.waitFor();
                    InputStream is = proc.getInputStream();
                    StringBuffer sbuf = new StringBuffer();
                    for ( int c; ( c = is.read() ) >= 0; ) {
                        sbuf.append( (char) c );
                    }
                    is.close();
                    userprofile = sbuf.toString();
                }
                catch ( Throwable e2 ) {
                    userprofile = null;
                }
            }
            if ( userprofile != null && userprofile.trim().length() > 0 ) {
                return new File( userprofile );
            }
            else {
                return new File( System.getProperty( "user.home" ) );
            }
        }
    }

    private static boolean isWindows() {
        String osname = System.getProperty( "os.name" );
        if ( osname.toLowerCase().startsWith( "windows" ) ||
             osname.toLowerCase().indexOf( "microsoft" ) >= 0 ) {
            return true;
        }
        else {
            return false;
        }
    }
}
