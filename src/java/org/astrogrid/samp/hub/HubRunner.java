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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.gui.GuiHubService;

public class HubRunner {

    private final HubService hub_;
    private final File lockfile_;
    private WebServer server_;
    private LockInfo lockInfo_;
    private boolean shutdown_;

    private final static Logger logger_ =
        Logger.getLogger( HubRunner.class.getName() );

    public HubRunner( HubService hub, File lockfile ) {
        hub_ = hub;
        lockfile_ = lockfile;
    }

    public void start() throws IOException {
        if ( lockfile_ != null && lockfile_.exists() ) {
            if ( isHubAlive( lockfile_ ) ) {
                throw new SampException( "A hub is already running" );
            }
            else {
                logger_.warning( "Overwriting " + lockfile_ + " lockfile "
                               + "for apparently dead hub" );
                lockfile_.delete();
            }
        }
        int port = SampUtils.getUnusedPort( 2112 );
        try {
            server_ = new WebServer( port );
            server_.start();
        }
        catch ( Exception e ) {
            throw new SampException( "Can't start XML-RPC server", e );
        }
        hub_.start();
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubRunner shutdown" ) {
            public void run() {
                shutdown();
            }
        } );
        String secret = hub_.getPassword();
        URL url =
            new URL( "http://" + SampUtils.getLocalhost() + ":" + port + "/" );
        server_.addHandler( "samp.hub", new HubXmlRpcHandler( hub_ ) );
        lockInfo_ = new LockInfo( secret, url.toString() );
        lockInfo_.put( "hub.impl", hub_.getClass().getName() );
        lockInfo_.put( "hub.start.millis",
                       Long.toString( System.currentTimeMillis() ) );
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

    public synchronized void shutdown() {
        if ( shutdown_ ) {
            return;
        }
        shutdown_ = true;
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
        if ( hub_ != null ) {
            try {
                hub_.shutdown();
            }
            catch ( Throwable e ) {
                logger_.log( Level.WARNING, "Hub service shutdown failed", e );
            }
        }
        if ( server_ != null ) {
            try {
                server_.shutdown();
            }
            catch ( Throwable e ) {
                logger_.log( Level.WARNING, "XMLRPC server shutdown failed",
                             e );
            }
        }
    }

    public HubService getHub() {
        return hub_;
    }

    public LockInfo getLockInfo() {
        return lockInfo_;
    }

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

    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }

    public static int runMain( String[] args ) throws IOException {
        String usage = new StringBuffer()
            .append( "   " )
            .append( "Usage: " )
            .append( HubRunner.class.getName() )
            .append( " [-help]" )
            .append( " [-gui]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        boolean gui = false;
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-gui" ) ) {
                it.remove();
                gui = true;
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
        runHub( gui );
        return 0;
    }

    public static void runHub( boolean gui ) throws IOException {
        File lockfile = SampUtils.getLockFile();
        
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
            hubService = new GuiHubService() {
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
            hubService = new BasicHubService();
        }
        HubRunner runner = new HubRunner( hubService, SampUtils.getLockFile() );
        hubRunners[ 0 ] = runner;
        runner.start();
    }
}
