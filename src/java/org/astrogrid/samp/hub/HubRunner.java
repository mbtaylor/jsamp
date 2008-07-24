package org.astrogrid.samp.hub;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
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
import javax.swing.JFrame;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.gui.GuiHubService;

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

    private final HubService hub_;
    private final File lockfile_;
    private LockInfo lockInfo_;
    private WebServer server_;
    private boolean shutdown_;

    private final static Logger logger_ =
        Logger.getLogger( HubRunner.class.getName() );
    private final static Random random_ = createRandom();

    /**
     * Constructor.
     * If the supplied <code>lockfile</code> is null, no lockfile will
     * be written at hub startup.
     *
     * @param   hub   object providing hub services
     * @param   lockfile  location to use for hub lockfile, or null
     */
    public HubRunner( HubService hub, File lockfile ) {
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
            if ( isHubAlive( lockfile_ ) ) {
                throw new IOException( "A hub is already running" );
            }
            else {
                logger_.warning( "Overwriting " + lockfile_ + " lockfile "
                               + "for apparently dead hub" );
                lockfile_.delete();
            }
        }

        // Start up server.
        int port = SampUtils.getUnusedPort( 2112 );
        try {
            server_ = new WebServer( port );
            server_.start();
        }
        catch ( Exception e ) {
            throw (IOException) new IOException( "Can't start XML-RPC server" )
                               .initCause( e );
        }

        // Start the hub service.
        hub_.start();
        String secret = createSecret();
        server_.addHandler( "samp.hub", new HubXmlRpcHandler( hub_, secret ) );

        // Ensure tidy up in case of JVM shutdown.
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubRunner shutdown" ) {
            public void run() {
                shutdown();
            }
        } );

        // Prepare lockfile information.
        URL url =
            new URL( "http://" + SampUtils.getLocalhost() + ":" + port + "/" );
        lockInfo_ = new LockInfo( secret, url.toString() );
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
                    LockInfo lockInfo = LockInfo.readLockFile();
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

        // Shut down the XML-RPC server process if it exists.
        if ( server_ != null ) {
            try {
                server_.shutdown();
                server_ = null;
            }
            catch ( Throwable e ) {
                logger_.log( Level.WARNING, "XMLRPC server shutdown failed",
                             e );
            }
        }
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
     * @param  lockfile  lockfile location
     * @return  true if the hub described at <code>lockfile</code> appears 
     *          to be alive and well
     */
    private static boolean isHubAlive( File lockfile ) {
        LockInfo info;
        try { 
            info = LockInfo
                  .readLockFile( new BufferedInputStream(
                                     new FileInputStream( lockfile ) ) );
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
                XmlRpcClientLite client = new XmlRpcClientLite( xurl );
                client.execute( "samp.hub.ping", new Vector() );
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
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( HubRunner.class.getName() )
            .append( " [-help]" )
            .append( " [-/+verbose ...]" )
            .append( " [-gui]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        boolean gui = false;
        int verbAdjust = 0;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-gui" ) ) {
                it.remove();
                gui = true;
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

        runHub( gui );
        return 0;
    }

    /**
     * Static method which may be used to start a SAMP hub programmatically.
     * If the <code>gui</code> flag is set, a window will be posted
     * which displays the current status of the hub.
     * When this window is disposed, the hub will stop.
     *
     * @param   gui   if true, display a window showing hub status
     */
    public static void runHub( boolean gui ) throws IOException {
        final BasicHubService hubService;
        final HubRunner[] hubRunners = new HubRunner[ 1 ];
        if ( gui ) {
            final WindowListener closeWatcher = new WindowAdapter() {
                public void windowClosed( WindowEvent evt ) {
                    HubRunner runner = hubRunners[ 0 ];
                    if ( runner != null ) {
                        runner.shutdown();
                    }
                }
            };
            hubService = new GuiHubService( random_ ) {
                public void start() {
                    super.start();
                    JFrame frame = createHubWindow();
                    frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
                    frame.addWindowListener( closeWatcher );
                    frame.setVisible( true );
                }
            };
        }
        else {
            hubService = new BasicHubService( random_ );
        }
        HubRunner runner = new HubRunner( hubService, SampUtils.getLockFile() );
        hubRunners[ 0 ] = runner;
        runner.start();
    }
}
