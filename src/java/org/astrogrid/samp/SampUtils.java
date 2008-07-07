package org.astrogrid.samp;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SampUtils {

    public static final String LOCKFILE_NAME = ".samp";
    public static final String LOCALHOST_PROP = "samp.localhost";

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
        return new File( Platform.getPlatform().getHomeDirectory(),
                         LOCKFILE_NAME );
    }

    public static String getLocalhost() {
        String hostname = System.getProperty( LOCALHOST_PROP, "" );
        if ( hostname.length() == 0 ) {
            try {
                hostname = InetAddress.getLocalHost().getCanonicalHostName();
            }
            catch ( UnknownHostException e ) {
                hostname = "127.0.0.1";
            }
        }
        return hostname;
    }

    /**
     * Returns an unused port number on the local host.
     *
     * @param   startPort  suggested port number; may or may not be used
     * @return  unused port
     */
    public static int getUnusedPort( int startPort ) throws IOException {
        return true ? findAnyPort()
                    : scanForPort( startPort );
    }
       
    private static int findAnyPort() throws IOException {
        ServerSocket socket = new ServerSocket( 0 );
        try {
            return socket.getLocalPort();
        }
        finally {
            try {
                socket.close();
            }
            catch ( IOException e ) {
            }
        }
    }

    /**
     * Two problems with this one - it may be a bit inefficient, and 
     * there's an annoying bug in the Apache XML-RPC WebServer class
     * which causes it to print "java.util.NoSuchElementException" to
     * the server's System.err for every port scanned by this routine 
     * that an org.apache.xmlrpc.WebServer server is listening on.
     */
    private static int scanForPort( int startPort ) throws IOException {
        final int nTry = 20;
        for ( int iPort = startPort; iPort < startPort + nTry; iPort++ ) {
            try {
                Socket trySocket = new Socket( "localhost", iPort );
                if ( ! trySocket.isClosed() ) {
                    trySocket.shutdownOutput();
                    trySocket.shutdownInput();
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
}
