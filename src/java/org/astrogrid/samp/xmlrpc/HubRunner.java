package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.LockWriter;

/**
 * Runs a SAMP hub using the SAMP Standard Profile.
 * The {@link #start} method must be called to start it up.
 *
 * <p>The {@link #main} method can be used to launch a hub from
 * the command line.  Use the <code>-help</code> flag for more information.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class HubRunner {

    private final SampXmlRpcClientFactory xClientFactory_;
    private final SampXmlRpcServerFactory xServerFactory_;
    private final HubService hub_;
    private final File lockfile_;
    private LockInfo lockInfo_;
    private SampXmlRpcServer server_;
    private HubXmlRpcHandler hubHandler_;
    private boolean shutdown_;

    private final static Logger logger_ =
        Logger.getLogger( HubRunner.class.getName() );
    private final static Random random_ = createRandom();

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
        hubHandler_ = new HubXmlRpcHandler( xClientFactory_, hub_, secret );
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
            LockWriter writer = new LockWriter( out );
            try {
                writer.writeComment( "SAMP Standard Profile lockfile written "
                                   + new Date() );
                out.flush();
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
                writer.writeAssignments( lockInfo_ );
            }
            finally {
                try {
                    if ( writer != null ) {
                        writer.close();
                    }
                    else if ( out != null ) {
                        out.close();
                    }
                }
                catch ( IOException e ) {
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
                    LockInfo lockInfo = LockInfo.readLockFile( lockfile_ );
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
            info = LockInfo.readLockFile( lockfile );
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
     * Returns a new, randomly seeded, Random object.
     *
     * @return  random
     */
    static Random createRandom() {
        byte[] seedBytes = new SecureRandom().generateSeed( 8 );
        long seed = 0L;
        for ( int i = 0; i < 8; i++ ) {
            seed = ( seed << 8 ) | ( seedBytes[ i ] & 0xff );
        }
        return new Random( seed );
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
            .append( " [-xmlrpc apache|internal]" )
            .append( "\n           " )
            .append( " [-mode " );
        HubMode[] modes = HubMode.getAvailableModes();
        for ( int im = 0; im < modes.length; im++ ) {
            if ( im > 0 ) {
                ubuf.append( '|' );
            }
            ubuf.append( modes[ im ].getName() );
        }
        ubuf.append( ']' );
        ubuf.append( "\n" );
        String usage = ubuf.toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        HubMode hubMode = HubMode.MESSAGE_GUI;
        if ( ! Arrays.asList( HubMode.getAvailableModes() )
                     .contains( hubMode ) ) {
            hubMode = HubMode.NO_GUI;
        }
        int verbAdjust = 0;
        XmlRpcKit xmlrpc = null;
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
            else if ( arg.equals( "-xmlrpc" ) && it.hasNext() ) {
                it.remove();
                String impl = (String) it.next();
                try {
                    xmlrpc = XmlRpcKit.getInstanceByName( impl );
                }
                catch ( Exception e ) {
                    logger_.log( Level.INFO, "No XMLRPC implementation " + impl,
                                 e );
                    System.err.println( usage );
                    return 1;
                }
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

        // Start the hub.
        runHub( hubMode, xmlrpc );

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
     * If the hub mode corresponds to one of the GUI options,
     * a window will be posted which displays the current status of the hub.
     * When this window is disposed, the hub will stop.
     *
     * @param   hubMode  hub mode
     * @param   xmlrpc  XML-RPC implementation;
     *                  automatically determined if null
     */
    public static void runHub( HubMode hubMode, XmlRpcKit xmlrpc )
            throws IOException {
        if ( xmlrpc == null ) {
            xmlrpc = XmlRpcKit.getInstance();
        }
        HubRunner[] hubRunners = new HubRunner[ 1 ];
        HubRunner runner =
            new HubRunner( xmlrpc.getClientFactory(), xmlrpc.getServerFactory(),
                           hubMode.createHubService( random_, hubRunners ),
                           SampUtils.getLockFile() );
        hubRunners[ 0 ] = runner;
        runner.start();
    }

    /**
     * Static method which will attempt to start a hub running in 
     * an external JVM.  The resulting hub can therefore outlast the
     * lifetime of the current application.
     * Because of the OS interaction required, it's hard to make this
     * bulletproof, and it may fail without an exception, but we do our best.
     *
     * @param   hubMode  hub mode
     */
    public static void runExternalHub( HubMode hubMode ) throws IOException {
        File javaHome = new File( System.getProperty( "java.home" ) );
        File javaExec = new File( new File( javaHome, "bin" ), "java" );
        String javacmd = ( javaExec.exists() && ! javaExec.isDirectory() )
                       ? javaExec.toString()
                       : "java";
        String[] propagateProps = new String[] { XmlRpcKit.IMPL_PROP, };
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
        argList.add( System.getProperty( "java.class.path" ) );
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
        Runtime.getRuntime().exec( args );
    }
}
