package org.astrogrid.samp.xmlrpc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.Hub;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.KeyGenerator;
import org.astrogrid.samp.hub.LockWriter;
import org.astrogrid.samp.hub.ProfileToken;
import org.astrogrid.samp.httpd.ServerResource;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * Runs a SAMP hub using the SAMP Standard Profile.
 * The {@link #start} method must be called to start it up.
 *
 * <p>The {@link #main} method can be used to launch a hub from
 * the command line.  Use the <code>-help</code> flag for more information.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 * @deprecated  use {@link org.astrogrid.samp.hub.Hub} instead
 */
public class HubRunner {

    private final SampXmlRpcClientFactory xClientFactory_;
    private final SampXmlRpcServerFactory xServerFactory_;
    private final HubService hub_;
    private final File lockfile_;
    private URL lockUrl_;
    private LockInfo lockInfo_;
    private SampXmlRpcServer server_;
    private HubXmlRpcHandler hubHandler_;
    private boolean shutdown_;

    private final static ProfileToken STANDARD_PROFILE = new ProfileToken() {
        public String getProfileName() {
            return "Standard";
        }
    };
    private final static Logger logger_ =
        Logger.getLogger( HubRunner.class.getName() );
    private final static Random random_ = KeyGenerator.createRandom();

    /**
     * Constructor.
     * If the supplied <code>lockfile</code> is null, no lockfile will
     * be written at hub startup.
     *
     * @param   xClientFactory   XML-RPC client factory implementation
     * @param   xServerFactory  XML-RPC server implementation
     * @param   hub   object providing hub services
     * @param   lockfile  location to use for hub lockfile, or null
     */
    public HubRunner( SampXmlRpcClientFactory xClientFactory, 
                      SampXmlRpcServerFactory xServerFactory,
                      HubService hub, File lockfile ) {
        xClientFactory_ = xClientFactory;
        xServerFactory_ = xServerFactory;
        hub_ = hub;
        lockfile_ = lockfile;
        logger_.warning( "Class " + HubRunner.class.getName()
                       + " is deprecated; use "
                       + Hub.class.getName() + " instead." );
    }

    /**
     * Starts the hub and writes the lockfile.
     *
     * @throws  IOException  if a hub is already running or an error occurs
     */
    public void start() throws IOException {

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

        // Start the hub service.
        hub_.start();
        String secret = createSecret();
        ClientProfile connectionFactory = new ClientProfile() {
            public HubConnection register() throws SampException {
                return hub_.register( STANDARD_PROFILE );
            }
            public boolean isHubRunning() {
                return hub_.isHubRunning();
            }
        };
        hubHandler_ =
            new HubXmlRpcHandler( xClientFactory_, connectionFactory, secret,
                                  new KeyGenerator( "k:", 16, random_ ) );
        server_.addHandler( hubHandler_ );

        // Ensure tidy up in case of JVM shutdown.
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubRunner shutdown" ) {
            public void run() {
                shutdown();
            }
        } );

        // Prepare lockfile information.
        lockInfo_ = new LockInfo( secret, server_.getEndpoint().toString() );
        lockInfo_.put( "hub.impl", hub_.getClass().getName() );
        lockInfo_.put( "hub.start.date", new Date().toString() );

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
    }

    /**
     * Shuts down the hub and tidies up.
     * May harmlessly be called multiple times.
     */
    public synchronized void shutdown() {

        // Return if we have already done this.
        if ( shutdown_ ) {
            return;
        }
        shutdown_ = true;

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

        // Shut down the hub service if exists.  This sends out shutdown
        // messages to registered clients.
        if ( hub_ != null ) {
            try {
                hub_.shutdown();
            }
            catch ( Throwable e ) {
                logger_.log( Level.WARNING, "Hub service shutdown failed", e );
            }
        }

        // Remove the hub XML-RPC handler from the server.
        if ( hubHandler_ != null && server_ != null ) {
            server_.removeHandler( hubHandler_ );
            server_ = null;
        }
        lockInfo_ = null;
    }

    /**
     * Returns the HubService object used by this runner.
     *
     * @return  hub service
     */
    public HubService getHub() {
        return hub_;
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
     * Used to generate the registration password.  May be overridden.
     *
     * @return  pasword
     */
    public String createSecret() {
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

    /**
     * Main method.  Starts a hub.
     * Use "-help" flag for more information.
     *
     * @param  args  command-line arguments
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }

    /**
     * Does the work for running the {@link #main} method.
     * System.exit() is not called from this method.
     * Use "-help" flag for more information.
     *
     * @param  args  command-line arguments
     * @return  0 means success, non-zero means error status
     */
    public static int runMain( String[] args ) throws IOException {
        StringBuffer ubuf = new StringBuffer();
        ubuf.append( "\n   Usage:" )
            .append( "\n      " )
            .append( HubRunner.class.getName() )
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n           " )
            .append( " [-mode " );
        HubMode[] modes = HubMode.getAvailableModes();
        for ( int im = 0; im < modes.length; im++ ) {
            if ( im > 0 ) {
                ubuf.append( '|' );
            }
            ubuf.append( modes[ im ].getName() );
        }
        ubuf.append( ']' )
            .append( " [-secret <secret>]" )
            .append( " [-httplock]" )
            .append( "\n" );
        String usage = ubuf.toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        HubMode hubMode = HubMode.MESSAGE_GUI;
        if ( ! Arrays.asList( HubMode.getAvailableModes() )
                     .contains( hubMode ) ) {
            hubMode = HubMode.NO_GUI;
        }
        int verbAdjust = 0;
        XmlRpcKit xmlrpc = null;
        String secret = null;
        boolean httplock = false;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-mode" ) && it.hasNext() ) {
                it.remove();
                String mode = (String) it.next();
                it.remove();
                hubMode = HubMode.getModeFromName( mode );
                if ( hubMode == null ) {
                    System.err.println( "Unknown mode " + mode );
                    System.err.println( usage );
                    return 1;
                }
            }
            else if ( arg.equals( "-secret" ) && it.hasNext() ) {
                it.remove();
                secret = (String) it.next();
                it.remove();
            }
            else if ( arg.equals( "-httplock" ) ) {
                it.remove();
                httplock = true;
            }
            else if ( arg.startsWith( "-v" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.startsWith( "+v" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.startsWith( "-h" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
            else {
                System.err.println( usage );
                return 1;
            }
        }
        assert argList.isEmpty();

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Get the location of the lockfile to write, if any.
        final File lockfile;
        if ( httplock ) {
            lockfile = null;
        }
        else {
            URL lockUrl = StandardClientProfile.getLockUrl();
            File f = SampUtils.urlToFile( lockUrl );
            if ( f == null ) {
                System.err.println( "Can't write lockfile to " + lockUrl );
                System.err.println( "Try resetting " 
                                  + DefaultClientProfile.HUBLOC_ENV
                                  + " environment variable." );
                return 1;
            }
            else {
                lockfile = f;
            }
        }

        // Start the hub.
        HubRunner runner = runHub( hubMode, xmlrpc, secret, lockfile );

        // If the lockfile is not the default one, write a message through
        // the logging system.
        URL lockfileUrl = httplock ? runner.publishLockfile()
                                   : SampUtils.fileToUrl( lockfile );
        boolean isDflt = StandardClientProfile.getDefaultLockUrl().toString()
                        .equals( lockfileUrl.toString() );
        String hubassign = DefaultClientProfile.HUBLOC_ENV + "="
                         + StandardClientProfile.STDPROFILE_HUB_PREFIX
                         + lockfileUrl;
        logger_.log( isDflt ? Level.INFO : Level.WARNING, hubassign );

        // For non-GUI case block indefinitely otherwise the hub (which uses
        // a daemon thread) will not just exit immediately.
        if ( hubMode.isDaemon() ) {
            Object lock = new String( "Indefinite" );
            synchronized ( lock ) {
                try {
                    lock.wait();
                }
                catch ( InterruptedException e ) {
                }
            }
        }

        // Success return.
        return 0;
    }

    /**
     * Static method which may be used to start a SAMP hub programmatically.
     * The returned hub is running (<code>start</code> has been called).
     *
     * <p>If the hub mode corresponds to one of the GUI options,
     * one of two things will happen.  An attempt will be made to install
     * an icon in the "system tray"; if this is successful, the attached
     * popup menu will provide options for displaying the hub window and
     * for shutting it down.  If no system tray is available, the hub window
     * will be posted directly, and the hub will shut down when this window
     * is closed.  System tray functionality is only available when running
     * under Java 1.6 or later, and when using a suitable display manager.
     *
     * @param   hubMode  hub mode
     * @param   xmlrpc  XML-RPC implementation;
     *                  automatically determined if null
     * @return  running hub
     */
    public static HubRunner runHub( HubMode hubMode, XmlRpcKit xmlrpc )
            throws IOException {
        return runHub( hubMode, xmlrpc, null,
                       SampUtils
                      .urlToFile( StandardClientProfile.getLockUrl() ) );
    }

    /**
     * Static method which may be used to start a SAMP hub programmatically,
     * with a supplied samp.secret string.
     * The returned hub is running (<code>start</code> has been called).
     *
     * <p>If the hub mode corresponds to one of the GUI options,
     * one of two things will happen.  An attempt will be made to install
     * an icon in the "system tray"; if this is successful, the attached
     * popup menu will provide options for displaying the hub window and
     * for shutting it down.  If no system tray is available, the hub window
     * will be posted directly, and the hub will shut down when this window
     * is closed.  System tray functionality is only available when running
     * under Java 1.6 or later, and when using a suitable display manager.
     *
     * @param   hubMode  hub mode
     * @param   xmlrpc  XML-RPC implementation;
     *                  automatically determined if null
     * @param   secret   samp.secret string to be used for hub connection;
     *                   chosen at random if null
     * @param   lockfile location of lockfile to write,
     *                   or null for lock to be provided by HTTP
     * @return  running hub
     */
    public static HubRunner runHub( HubMode hubMode, XmlRpcKit xmlrpc,
                                    final String secret, File lockfile )
            throws IOException {
        if ( xmlrpc == null ) {
            xmlrpc = XmlRpcKit.getInstance();
        }
        HubRunner[] hubRunners = new HubRunner[ 1 ];
        HubRunner runner =
            new HubRunner( xmlrpc.getClientFactory(), xmlrpc.getServerFactory(),
                           hubMode.createHubService( random_, hubRunners ),
                           lockfile ) {
                public String createSecret() {
                    return secret == null ? super.createSecret()
                                          : secret;
                }
            };
        hubRunners[ 0 ] = runner;
        runner.start();
        return runner;
    }

    /**
     * Static method which will attempt to start a hub running in 
     * an external JVM.  The resulting hub can therefore outlast the
     * lifetime of the current application.
     * Because of the OS interaction required, it's hard to make this
     * bulletproof, and it may fail without an exception, but we do our best.
     *
     * @param   hubMode  hub mode
     * @see     #checkExternalHubAvailability
     */
    public static void runExternalHub( HubMode hubMode ) throws IOException {
        String classpath = System.getProperty( "java.class.path" );
        if ( classpath == null || classpath.trim().length() == 0 ) {
            throw new IOException( "No classpath available - JNLP context?" );
        }
        File javaHome = new File( System.getProperty( "java.home" ) );
        File javaExec = new File( new File( javaHome, "bin" ), "java" );
        String javacmd = ( javaExec.exists() && ! javaExec.isDirectory() )
                       ? javaExec.toString()
                       : "java";
        String[] propagateProps = new String[] {
            XmlRpcKit.IMPL_PROP,
            UtilServer.PORT_PROP,
            SampUtils.LOCALHOST_PROP,
            "java.awt.Window.locationByPlatform",
        };
        List argList = new ArrayList();
        argList.add( javacmd );
        for ( int ip = 0; ip < propagateProps.length; ip++ ) {
            String propName = propagateProps[ ip ];
            String propVal = System.getProperty( propName );
            if ( propVal != null ) {
                argList.add( "-D" + propName + "=" + propVal );
            }
        }
        argList.add( "-classpath" );
        argList.add( classpath );
        argList.add( HubRunner.class.getName() );
        argList.add( "-mode" );
        argList.add( hubMode.toString() );
        String[] args = (String[]) argList.toArray( new String[ 0 ] );
        StringBuffer cmdbuf = new StringBuffer();
        for ( int iarg = 0; iarg < args.length; iarg++ ) {
            if ( iarg > 0 ) {
                cmdbuf.append( ' ' );
            }
            cmdbuf.append( args[ iarg ] );
        }
        logger_.info( "Starting external hub" );
        logger_.info( cmdbuf.toString() );
        execBackground( args );
    }

    /**
     * Attempts to determine whether an external hub can be started using
     * {@link #runExternalHub}.  If it can be determined that such an
     * attempt would fail, this method will throw an exception with
     * an informative message.  This method succeeding is not a guarantee
     * that an external hub can be started successfullly.
     * The behaviour of this method is not expected to change over the
     * lifetime of a given JVM.
     */
    public static void checkExternalHubAvailability() throws IOException {
        String classpath = System.getProperty( "java.class.path" );
        if ( classpath == null || classpath.trim().length() == 0 ) {
            throw new IOException( "No classpath available - JNLP context?" );
        }
        if ( System.getProperty( "jnlpx.jvm" ) != null ) {
            throw new IOException( "Running under WebStart"
                                 + " - external hub not likely to work" );
        }
    }

    /**
     * Executes a command in a separate process, and discards any stdout
     * or stderr output generated by it.
     * Simply calling <code>Runtime.exec</code> can block the process
     * until its output is consumed.
     *
     * @param  cmdarray  array containing the command to call and its args
     */
    private static void execBackground( String[] cmdarray ) throws IOException {
        Process process = Runtime.getRuntime().exec( cmdarray );
        discardBytes( process.getInputStream() );
        discardBytes( process.getErrorStream() );
    }

    /**
     * Ensures that any bytes from a given input stream are discarded.
     *
     * @param  in  input stream
     */
    private static void discardBytes( final InputStream in ) {
        Thread eater = new Thread( "StreamEater" ) {
            public void run() {
                try {
                    while ( in.read() >= 0 ) {}
                    in.close();
                }
                catch ( IOException e ) {
                }
            }
        };
        eater.setDaemon( true );
        eater.start();
    }
}
