package org.astrogrid.samp.hub;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.astrogrid.samp.LockInfo;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.gui.GuiHubService;

public class HubRunner {

    private final HubService hub_;
    private final File lockfile_;
    private final WebServer server_;
    private final LockInfo lockInfo_;
    private boolean shutdown_;

    private final static Logger logger_ =
        Logger.getLogger( HubRunner.class.getName() );

    public HubRunner( HubService hub, File lockfile ) throws IOException {
        hub_ = hub;
        lockfile_ = lockfile;
        if ( lockfile != null && lockfile.exists() ) {
            if ( isHubAlive( lockfile ) ) {
                throw new SampException( "A hub is already running" );
            }
            else {
                logger_.warning( "Overwriting " + lockfile + " lockfile "
                               + "for apparently dead hub" );
                lockfile.delete();
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
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubRunner shutdown" ) {
            public void run() {
                shutdown();
            }
        } );
        String secret = hub.getPassword();
        URL url = new URL( "http://"
                         + InetAddress.getLocalHost().getCanonicalHostName()
                         + ":" + port + "/" );
        server_.addHandler( "samp.hub", new HubXmlRpcHandler( hub ) );
        lockInfo_ = new LockInfo( secret, url.toString() );
        lockInfo_.put( "hub.impl", hub.getClass().getName() );
        lockInfo_.put( "hub.start.millis",
                       Long.toString( System.currentTimeMillis() ) );
        if ( lockfile != null ) {
            logger_.info( "Writing new lockfile " + lockfile );
            FileOutputStream out = new FileOutputStream( lockfile );
            LockWriter writer = new LockWriter( out );
            try {
                writer.writeComment( "SAMP Standard Profile lockfile written "
                                   + new Date() );
                out.flush();
                try {
                    LockWriter.setLockPermissions( lockfile );
                    logger_.info( "Lockfile permissions set to "
                                + "user access only" );
                }
                catch ( IOException e ) {
                    logger_.log( Level.WARNING,
                                 "Failed attempt to change " + lockfile
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
        
        HubService hubService = new BasicHubService();
        if ( gui ) {
            hubService = new GuiHubService( hubService );
        }
        hubService.start();
        new HubRunner( hubService, SampUtils.getLockFile() );
        return 0;
    }
}
