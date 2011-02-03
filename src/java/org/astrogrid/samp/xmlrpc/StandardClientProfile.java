package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.Platform;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Standard Profile implementation of ClientProfile.
 * It is normally appropriate to use one of the static methods
 * to obtain an instance based on a particular XML-RPC implementation.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class StandardClientProfile implements ClientProfile {

    private final SampXmlRpcClientFactory xClientFactory_;
    private final SampXmlRpcServerFactory xServerFactory_;

    private static StandardClientProfile defaultInstance_;
    private static URL dfltLockUrl_;
    private static URL lockUrl_;
    private static final Logger logger_ =
        Logger.getLogger( StandardClientProfile.class.getName() );

    /** Filename used for lockfile in home directory by default ({@value}). */
    public static final String LOCKFILE_NAME = ".samp";

    /** Prefix in SAMP_HUB value indicating lockfile URL ({@value}). */
    public static final String STDPROFILE_HUB_PREFIX = "std-lockurl:";

    /**
     * Constructs a profile given client and server factory implementations.
     *
     * @param   xClientFactory   XML-RPC client factory implementation
     * @param   xServerFactory   XML-RPC server factory implementation
     */
    public StandardClientProfile( SampXmlRpcClientFactory xClientFactory,
                                  SampXmlRpcServerFactory xServerFactory ) {
        xClientFactory_ = xClientFactory;
        xServerFactory_ = xServerFactory;
    }

    /**
     * Constructs a profile given an XmlRpcKit object.
     *
     * @param  xmlrpc  XML-RPC implementation
     */
    public StandardClientProfile( XmlRpcKit xmlrpc ) {
        this( xmlrpc.getClientFactory(), xmlrpc.getServerFactory() );
    }

    public boolean isHubRunning() {
        try {
            return getLockInfo() != null;
        }
        catch ( IOException e ) {
            return false;
        }
    }

    public HubConnection register() throws SampException {
        LockInfo lockInfo;
        try {
            lockInfo = getLockInfo();
        }
        catch ( SampException e ) {
            throw (SampException) e;
        }
        catch ( IOException e ) {
            throw new SampException( "Error reading lockfile", e );
        }
        if ( lockInfo == null ) {
            return null;
        }
        else {
            try {
                lockInfo.check();
            }
            catch ( DataException e ) {
                throw new SampException( "Incomplete/broken lock file", e );
            }
            SampXmlRpcClient xClient;
            URL xurl = lockInfo.getXmlrpcUrl();
            try {
                xClient = xClientFactory_.createClient( xurl );
            }
            catch ( IOException e ) {
                throw new SampException( "Can't connect to " + xurl, e );
            }
            return new StandardHubConnection( xClient, xServerFactory_,
                                              lockInfo.getSecret() );
        }
    }

    /**
     * Returns the LockInfo which indicates how to locate the hub.
     * If no lockfile exists (probably becuause no appropriate hub
     * is running), null is returned.
     * The default implementation returns 
     * <code>LockInfo.readLockFile(getLockUrl())</code>;
     * it may be overridden to provide a non-standard client profiles.
     *
     * @return   hub location information
     * @throws  IOException  if the lockfile exists but cannot be read for
     *          some reason
     */
    public LockInfo getLockInfo() throws IOException {
        return LockInfo.readLockFile( getLockUrl() );
    }

    /**
     * Returns the location of the Standard Profile lockfile.
     * By default this is the file <code>.samp</code> in the user's "home"
     * directory, unless overridden by a value of the SAMP_HUB environment
     * variable starting with "std-lockurl".
     *
     * @return   lockfile URL
     */
    public static URL getLockUrl() throws IOException {
        if ( lockUrl_ == null ) {
            String hublocEnv = DefaultClientProfile.HUBLOC_ENV;
            String hubloc = Platform.getPlatform().getEnv( hublocEnv );
            final URL lockUrl;
            if ( hubloc != null &&
                 hubloc.startsWith( STDPROFILE_HUB_PREFIX ) ) {
                lockUrl = new URL( hubloc.substring( STDPROFILE_HUB_PREFIX
                                                    .length() ) );
                logger_.info( "Lockfile as set by env var: " 
                            + hublocEnv + "=" + hubloc );
            }
            else if ( hubloc != null && hubloc.trim().length() > 0 ) {
                logger_.warning( "Ignoring non-Standard " + hublocEnv + "="
                               + hubloc );
                lockUrl = getDefaultLockUrl();
            }
            else {
                lockUrl = getDefaultLockUrl();
                logger_.info( "Using default Standard Profile lockfile: " 
                            + SampUtils.urlToFile( lockUrl ) );
            }
            lockUrl_ = lockUrl;
        }
        return lockUrl_;
    }

    /**
     * Returns the lockfile URL which will be used in absence of any
     * SAMP_HUB environment variable.
     *
     * @return   URL for file .samp in user's home directory
     */
    public static URL getDefaultLockUrl() throws IOException {
        if ( dfltLockUrl_ == null ) {
            dfltLockUrl_ =
                SampUtils.fileToUrl( new File( Platform.getPlatform()
                                                       .getHomeDirectory(),
                                              LOCKFILE_NAME ) );
        }
        return dfltLockUrl_;
    }

    /**
     * Returns an instance based on the default XML-RPC implementation.
     * This can be configured using system properties.
     *
     * @see   XmlRpcKit#getInstance
     * @see   org.astrogrid.samp.client.DefaultClientProfile#getProfile
     * @return  a client profile instance
     */
    public static StandardClientProfile getInstance() {
        if ( defaultInstance_ == null ) {
            XmlRpcKit xmlrpc = XmlRpcKit.getInstance();
            defaultInstance_ =
                new StandardClientProfile( xmlrpc.getClientFactory(),
                                           xmlrpc.getServerFactory() );
        }
        return defaultInstance_;
    }
}
