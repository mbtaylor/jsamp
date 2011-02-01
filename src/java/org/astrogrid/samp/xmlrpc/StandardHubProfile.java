package org.astrogrid.samp.xmlrpc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.ServerResource;
import org.astrogrid.samp.httpd.UtilServer;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.hub.LockWriter;

/**
 * HubProfile implementation for the SAMP Standard Profile.
 *
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public class StandardHubProfile implements HubProfile {

    private final SampXmlRpcClientFactory xClientFactory_;
    private final SampXmlRpcServerFactory xServerFactory_;
    private final File lockfile_;
    private final String secret_;
    private volatile boolean started_;
    private volatile boolean shutdown_;
    private URL lockUrl_;
    private SampXmlRpcServer server_;
    private HubXmlRpcHandler hubHandler_;
    private LockInfo lockInfo_;
    private static final Logger logger_ =
        Logger.getLogger( StandardHubProfile.class.getName() );
    private static final Random random_ = KeyGenerator.createRandom();

    /**
     * Constructs a hub profile with given configuration information.
     * If the supplied <code>lockfile</code> is null, no lockfile will
     * be written at hub startup.
     *
     * @param   xClientFactory   XML-RPC client factory implementation
     * @param   xServerFactory  XML-RPC server implementation
     * @param   lockfile  location to use for hub lockfile, or null
     * @param   secret   value for samp.secret lockfile key
     */
    public StandardHubProfile( SampXmlRpcClientFactory xClientFactory,
                               SampXmlRpcServerFactory xServerFactory,
                               File lockfile, String secret ) {
        xClientFactory_ = xClientFactory;
        xServerFactory_ = xServerFactory;
        lockfile_ = lockfile;
        secret_ = secret;
    }

    /**
     * Constructs a hub profile with default configuration.
     */
    public StandardHubProfile() throws IOException {
        this( XmlRpcKit.getInstance().getClientFactory(),
              XmlRpcKit.getInstance().getServerFactory(),
              SampUtils.urlToFile( StandardClientProfile.getLockUrl() ),
              createSecret() );
    }

    public void start( HubService hubService ) throws IOException {

        // Check state.
        synchronized ( this ) {
            if ( started_ ) {
                throw new IllegalStateException( "Already started" );
            }
            started_ = true;
        }

        // Check for running or moribund hub.
        if ( lockfile_ != null && lockfile_.exists() ) {
            if ( isHubAlive( xClientFactory_, lockfile_ ) ) {
                throw new IOException( "A hub is already running" );
            }
            else {
                logger_.warning( "Overwriting " + lockfile_ + " lockfile "
                               + "for apparently dead hub" );
                lockfile_.delete();
            }
        }

        // Start up server.
        try {
            server_ = xServerFactory_.getServer();
        }
        catch ( IOException e ) {
            throw e;
        }
        catch ( Exception e ) {
            throw (IOException) new IOException( "Can't start XML-RPC server" )
                               .initCause( e );
        }
        hubHandler_ =
            new HubXmlRpcHandler( xClientFactory_, hubService, secret_,
                                  new KeyGenerator( "k:", 16, random_ ) );
        server_.addHandler( hubHandler_ );

        // Prepare lockfile information.
        lockInfo_ = new LockInfo( secret_, server_.getEndpoint().toString() );
        lockInfo_.put( "hub.impl", hubService.getClass().getName() );
        lockInfo_.put( "profile.impl", this.getClass().getName() );
        lockInfo_.put( "profile.start.date", new Date().toString() );

        // Write lockfile information to file if required.
        if ( lockfile_ != null ) { 
            logger_.info( "Writing new lockfile " + lockfile_ );
            FileOutputStream out = new FileOutputStream( lockfile_ );
            try {
                writeLockInfo( lockInfo_, out );
                try {
                    LockWriter.setLockPermissions( lockfile_ );
                    logger_.info( "Lockfile permissions set to "
                                + "user access only" );
                }
                catch ( IOException e ) {
                    logger_.log( Level.WARNING,
                                 "Failed attempt to change " + lockfile_
                               + " permissions to user access only"
                               + " - possible security implications", e );
                }
            }
            finally {
                try {
                    out.close();
                }
                catch ( IOException e ) {
                    logger_.log( Level.WARNING, "Error closing lockfile?", e );
                }
            }
        }

        // If the lockfile is not the default one, write a message through
        // the logging system.
        URL lockfileUrl = lockfile_ == null
                        ? publishLockfile()
                        : SampUtils.fileToUrl( lockfile_ );
        boolean isDflt = StandardClientProfile.getDefaultLockUrl().toString()
                        .equals( lockfileUrl.toString() );
        String hubassign = StandardClientProfile.HUBLOC_ENV + "="
                         + StandardClientProfile.STDPROFILE_HUB_PREFIX
                         + lockfileUrl;
        logger_.log( isDflt ? Level.INFO : Level.WARNING, hubassign );
    }

    public void shutdown() {

        // Check state.
        synchronized ( this ) {
            if ( ! started_ ) {
                throw new IllegalStateException( "Not started" );
            }
            if ( shutdown_ ) {
                return;
            }
            shutdown_ = true;
        }

        // Delete the lockfile if it exists and if it is the one originally
        // written by this runner.
        if ( lockfile_ != null ) {
            if ( lockfile_.exists() ) {
                try {
                    LockInfo lockInfo = readLockFile( lockfile_ );
                    if ( lockInfo.getSecret()
                        .equals( lockInfo_.getSecret() ) ) {
                        assert lockInfo.equals( lockInfo_ );
                        boolean deleted = lockfile_.delete();
                        logger_.info( "Lockfile " + lockfile_ + " "
                                    + ( deleted ? "deleted"
                                                : "deletion attempt failed" ) );
                    }
                    else {
                        logger_.warning( "Lockfile " + lockfile_ + " has been "
                                       + " overwritten - not deleting" );
                    }
                }
                catch ( Throwable e ) {
                    logger_.log( Level.WARNING,
                                 "Failed to delete lockfile " + lockfile_,
                                 e );
                }
            }
            else {
                logger_.warning( "Lockfile " + lockfile_ + " has disappeared" );
            }
        }

        // Withdraw service of the lockfile, if one has been published.
        if ( lockUrl_ != null ) {
            try {
                UtilServer.getInstance().getResourceHandler()
                                        .removeResource( lockUrl_ );
            }
            catch ( IOException e ) {
                logger_.warning( "Failed to withdraw lockfile URL" );
            }
            lockUrl_ = null;
        }

        // Remove the hub XML-RPC handler from the server.
        if ( hubHandler_ != null && server_ != null ) {
            server_.removeHandler( hubHandler_ );
            server_ = null;
        }
        lockInfo_ = null;
    }

    /**
     * Returns the lockfile information associated with this object.
     * Only present after {@link #start} has been called.
     *
     * @return  lock info
     */
    public LockInfo getLockInfo() {
        return lockInfo_;
    }

    /**
     * Returns an HTTP URL at which the lockfile for this hub can be found.
     * The first call to this method causes the lockfile to be published
     * in this way; subsequent calls return the same value.
     *
     * <p>Use this with care; publishing your lockfile means that other people
     * can connect to your hub and potentially do disruptive things.
     *
     * @return  lockfile information URL
     */
    public URL publishLockfile() throws IOException {
        if ( lockUrl_ == null ) {
            ByteArrayOutputStream infoStrm = new ByteArrayOutputStream();
            writeLockInfo( lockInfo_, infoStrm );
            infoStrm.close();
            final byte[] infoBuf = infoStrm.toByteArray();
            URL url = UtilServer.getInstance().getResourceHandler()
                     .addResource( "samplock", new ServerResource() {
                public long getContentLength() {
                     return infoBuf.length;
                }
                public String getContentType() {
                     return "text/plain";
                }
                public void writeBody( OutputStream out ) throws IOException {
                    out.write( infoBuf );
                }
            } );

            // Attempt to replace whatever host name is used by the FQDN,
            // for maximal usefulness to off-host clients.
            try {
                url = new URL( url.getProtocol(),
                               InetAddress.getLocalHost()
                                          .getCanonicalHostName(),
                               url.getPort(), url.getFile() );
            }
            catch ( IOException e ) {
            }
            lockUrl_ = url;
        }
        return lockUrl_;
    }

    /**
     * Returns a string suitable for use as a Standard Profile Secret.
     *
     * @return  new secret
     */
    public static synchronized String createSecret() {
        return Long.toHexString( random_.nextLong() );
    }

    /**
     * Attempts to determine whether a given lockfile corresponds to a hub
     * which is still alive.
     *
     * @param  xClientFactory  XML-RPC client factory implementation
     * @param  lockfile  lockfile location
     * @return  true if the hub described at <code>lockfile</code> appears
     *          to be alive and well
     */
    private static boolean isHubAlive( SampXmlRpcClientFactory xClientFactory,
                                       File lockfile ) {
        LockInfo info;
        try {
            info = readLockFile( lockfile );
        }
        catch ( Exception e ) {
            logger_.log( Level.WARNING, "Failed to read lockfile", e );
            return false;
        }
        if ( info == null ) {
            return false;
        }
        URL xurl = info.getXmlrpcUrl();
        if ( xurl != null ) {
            try {
                xClientFactory.createClient( xurl )
                              .callAndWait( "samp.hub.ping", new ArrayList() );
                return true;
            }
            catch ( Exception e ) {
                logger_.log( Level.WARNING, "Hub ping method failed", e );
                return false;
            }
        }
        else {
            logger_.warning( "No XMLRPC URL in lockfile" );
            return false;
        }
    }

    /**
     * Reads lockinfo from a file.
     *
     * @param  lockFile  file
     * @return  info from file
     */
    private static LockInfo readLockFile( File lockFile ) throws IOException {
        return LockInfo.readLockFile( new FileInputStream( lockFile ) );
    }

    /**
     * Writes lockfile information to a given output stream.
     * The stream is not closed.
     *
     * @param   info  lock info to write
     * @param   out   destination stream
     */
    private static void writeLockInfo( LockInfo info, OutputStream out )
            throws IOException {
        LockWriter writer = new LockWriter( out );
        writer.writeComment( "SAMP Standard Profile lockfile written "
                           + new Date() );
        writer.writeComment( "Note contact URL hostname may be "
                           + "configured using "
                           + SampUtils.LOCALHOST_PROP + " property" );
        writer.writeAssignments( info );
        out.flush();
    }
}
