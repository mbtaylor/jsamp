package org.astrogrid.samp.web;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps track of which URLs have been seen in communications inbound to
 * and outbound from Web Profile clients.
 * On the basis of these observations it is able to advise whether a
 * Web Profile client ought to be permitted to dereference a given URL.
 * The idea is that a Web Profile client, which may not be entirely
 * trustworthy, has no legitimate reason for dereferencing an arbitrary
 * URL, and should only be permitted to dereference local URLs if they
 * have previously been sent as message arguments to it.
 * (so for instance an attempt to read file:///etc/password is likely to
 * be blocked).
 * Since a SAMP client may be able to provoke some kind of echo, any
 * URL which was mentioned by a Web Profile client before any other
 * client mentions it is automatically marked as suspicious.
 *
 * <p>Details of the implementation are arguable.
 * 
 * @author   Mark Taylor
 * @since    22 Jul 2011
 */
class UrlTracker {

    private final Set permittedSet_;
    private final Set blockedSet_;
    private final String[] localhostNames_;
    private final Logger logger_ =
            Logger.getLogger( UrlTracker.class.getName() );

    /**
     * Constructor.
     */
    public UrlTracker() {
        permittedSet_ = new HashSet();
        blockedSet_ = new HashSet();

        // Set up a list of aliases for the local host so we can identify
        // and prepare to restrict access to localhost URLs.
        List localNameList = new ArrayList();
        localNameList.add( "localhost" );
        localNameList.add( "127.0.0.1" );
        try {
            InetAddress localAddr = InetAddress.getLocalHost();
            localNameList.add( localAddr.getHostName() );
            localNameList.add( localAddr.getHostAddress() );
            localNameList.add( localAddr.getCanonicalHostName() );
        }
        catch ( UnknownHostException e ) {
            logger_.log( Level.WARNING, "Can't determine local host name", e );
        }
        localhostNames_ = (String[]) localNameList.toArray( new String[ 0 ] );
    }

    /**
     * Note that a URL has been communicated to a Web Profile client
     * from the outside world.
     *
     * @param   url  incoming URL
     */
    public synchronized void noteIncomingUrl( URL url ) {
        if ( isSensitive( url ) ) {
            if ( ! blockedSet_.contains( url ) ) {
                if ( permittedSet_.add( url ) ) {
                    logger_.config( "Mark for translate permission URL "
                                  + url );
                }
            }
        }
    }

    /**
     * Note that a Web Profile client has communicated a URL to the
     * outside world.
     *
     * @param  url  outgoing URL
     */
    public synchronized void noteOutgoingUrl( URL url ) {
        if ( isSensitive( url ) ) {
            if ( ! permittedSet_.contains( url ) ) {
                if ( blockedSet_.add( url ) ) {
                    logger_.config( "Mark for translate blocking URL " + url );
                }
            }
        }
    }

    /**
     * Indicates whether access to a given URL should be permitted,
     * according to the strategy implemented by this class,
     * from a Web Profile client.
     *
     * @param  url  URL to assess
     * @return   true iff permission to access is appropriate
     */
    public synchronized boolean isUrlPermitted( URL url ) {
        if ( isSensitive( url ) ) {
            if ( permittedSet_.contains( url ) ) {
                logger_.config( "Translation permitted for marked URL " + url );
                return true;
            }
            else {
                logger_.warning( "Translation denied for unmarked URL " + url );
                return false;
            }
        }
        else {
            logger_.config( "Translation permitted for non-sensitive URL "
                          + url );
            return true;
        }
    }

    /**
     * Indicates whether a given URL is potentially sensitive.
     * The current implementation always returns true.
     * This is probably correct, since it's not in general possible
     * to tell whether or not a given URL accords privileges to
     * requests from the local host.  But if this ends up letting
     * too much through, identifying only file URLs and http/https
     * ones on the local domain would probably be OK.
     *
     * @param  url   URL to assess
     * @return  true iff access should be restricted
     */
    protected boolean isSensitive( URL url ) {
        return true;
    }

    /**
     * Determines whether a hostname appears to reference the localhost.
     *
     * @param  host   hostname from URL
     * @return  true iff host appears to be, or may be, local
     */
    private boolean isLocalHost( String host ) {
        if ( host == null || host.length() == 0 ) {
            return true;
        }
        for ( int i = 0; i < localhostNames_.length; i++ ) {
            if ( host.equalsIgnoreCase( localhostNames_[ i ] ) ) {
                return true;
            }
        }
        return false;
    }
}
