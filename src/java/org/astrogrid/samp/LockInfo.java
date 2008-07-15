package org.astrogrid.samp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Represents the information read from a SAMP Standard Profile Lockfile.
 * This contains a key-value entry for each assignment read from the file.
 * Any non-assignment lines are not represented by this object.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class LockInfo extends SampMap {

    private static final Logger logger_ =
        Logger.getLogger( LockInfo.class.getName() );

    /** Key for opaque text string required by the hub for registration. */
    public static final String SECRET_KEY = "samp.secret";

    /** Key for XML-RPC endpoint for communication with the hub. */
    public static final String XMLRPCURL_KEY = "samp.hub.xmlrpc.url";

    /** Key for the SAMP Standard Profile version implemented by the hub. */
    public static final String VERSION_KEY = "samp.profile.version";

    private static final String[] KNOWN_KEYS = new String[] {
        SECRET_KEY,
        XMLRPCURL_KEY,
        VERSION_KEY,
    };

    /** SAMP Standard Profile version for this toolkit implementation. */
    public static final String DEFAULT_VERSION_VALUE = "1.0";

    private static final Pattern TOKEN_REGEX =
        Pattern.compile( "[a-zA-Z0-9\\-_\\.]+" );
    private static final Pattern ASSIGNMENT_REGEX =
        Pattern.compile( "(" + TOKEN_REGEX.pattern() + ")=(.*)" );
    private static final Pattern COMMENT_REGEX =
        Pattern.compile( "#[\u0020-\u007f]*" );

    /**
     * Constructs an empty LockInfo.
     */
    public LockInfo() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a LockInfo based on an existing map.
     *
     * @param  map  map containing initial data for this object
     */
    public LockInfo( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Constructs a LockInfo from a given SAMP secret and XML-RPC URL.
     * The version string is set to the default for this toolkit.
     *
     * @param   secret  value for {@link #SECRET_KEY} key
     * @param   xmlrpcurl  value for {@link #XMLRPCURL_KEY} key
     */
    public LockInfo( String secret, String xmlrpcurl ) {
        this();
        put( SECRET_KEY, secret );
        put( XMLRPCURL_KEY, xmlrpcurl );
        put( VERSION_KEY, DEFAULT_VERSION_VALUE );
    }

    /**
     * Returns the value of the {@link #XMLRPCURL_KEY} key.
     *
     * @return  hub XML-RPC connection URL
     */
    public URL getXmlrpcUrl() {
        return getUrl( XMLRPCURL_KEY );
    }

    /**
     * Returns the value of the {@link #VERSION_KEY} key.
     *
     * @return  version of the SAMP standard profile implemented
     */
    public String getVersion() {
        return getString( VERSION_KEY );
    }

    /**
     * Returns the value of the {@link #SECRET_KEY} key.
     *
     * @return  password for hub connection
     */
    public String getSecret() {
        return getString( SECRET_KEY );
    }

    public void check() {
        super.check();
        checkHasKeys( new String[] { SECRET_KEY, XMLRPCURL_KEY, } );
        for ( Iterator it = entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Object key = entry.getKey();
            if ( key instanceof String ) {
                if ( ! TOKEN_REGEX.matcher( key.toString() ).matches() ) {
                    throw new DataException( "Bad key syntax: " + key + 
                                             " does not match " +
                                             TOKEN_REGEX.pattern() );
                }
            }
            else {
                throw new DataException( "Map key " + entry.getKey()
                                       + " is not a string" );
            }
            Object value = entry.getValue();
            if ( value instanceof String ) {
                String sval = (String) value;
                for ( int i = 0; i < sval.length(); i++ ) {
                    int c = sval.charAt( i );
                    if ( c < 0x20 || c > 0x7f ) {
                        throw new DataException( "Value contains illegal "
                                               + "character 0x"
                                               + Integer.toHexString( c ) );
                    }
                }
            }
            else {
                throw new DataException( "Map value " + value +
                                         " is not a string" );
            }
        }
    }

    /**
     * Returns the LockInfo as read from the LockFile found in the usual place
     * {@link SampUtils#getLockFile}.
     * If the lockfile does not exist, null is returned.
     * An exception may be thrown if it exists but is cannot be read.
     *
     * @return  lockfile contents, or null if it is absent
     */
    public static LockInfo readLockFile() throws IOException {
        File file = SampUtils.getLockFile();
        return file.exists()
             ? readLockFile( new BufferedInputStream( 
                                 new FileInputStream( file ) ) )
             : null;
    }

    /**
     * Returns the LockInfo read from a given stream.
     * The stream is closed if the read is successful.
     *
     * @param   in  input stream to read
     * @return   lockfile information
     */
    public static LockInfo readLockFile( InputStream in ) throws IOException {
        LockInfo info = new LockInfo();
        for ( String line; ( line = readLine( in ) ) != null; ) {
            Matcher assigner = ASSIGNMENT_REGEX.matcher( line );
            if ( assigner.matches() ) {
                info.put( assigner.group( 1 ), assigner.group( 2 ) );
            }
            else if ( COMMENT_REGEX.matcher( line ).matches() ) {
            }
            else if ( line.length() == 0 ) {
            }
            else {
                logger_.warning( "Ignoring lockfile line with bad syntax" );
                logger_.info( "Bad line: " + line );
            }
        }
        in.close();
        return info;
    }

    /**
     * Returns a given map as a LockInfo object.
     *
     * @param  map  map
     * @return lock info
     */
    public static LockInfo asLockInfo( Map map ) {
        return map instanceof LockInfo ? (LockInfo) map
                                       : new LockInfo( map );
           
    }

    /**
     * Returns a line from a lockfile-type input stream.
     *
     * @param  in  input stream
     * @return  next line
     */
    private static String readLine( InputStream in ) throws IOException {
        StringBuffer sbuf = new StringBuffer();
        while ( true ) {
            int c = in.read();
            switch ( c ) {
                case '\r':
                case '\n':
                    return sbuf.toString();
                case -1:
                    return sbuf.length() > 0 ? sbuf.toString() : null;
                default:
                    sbuf.append( (char) c );
            }
        }
    }
}
