package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.HubService;

public class StandardHubProfileTest extends TestCase {

    public StandardHubProfileTest() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( org.astrogrid.samp.httpd.HttpServer.class.getName() )
              .setLevel( Level.SEVERE );
        Logger.getLogger( StandardHubProfile.class.getName() )
              .setLevel( Level.SEVERE );
    }

    public void testRunHub() throws IOException {
        File tmpfile = File.createTempFile( "tmp", ".samp" );
        assertTrue( tmpfile.delete() );
        runHub( tmpfile );
        runHub( null );
    }

    private void runHub( File lockfile ) throws IOException {
        final String secret = "it's-a-secret";
        XmlRpcKit xmlrpc = XmlRpcKit.INTERNAL;
        StandardHubProfile hubProf =
            new StandardHubProfile( xmlrpc.getClientFactory(),
                                    xmlrpc.getServerFactory(),
                                    lockfile, secret );
        if ( lockfile != null ) {
            assertTrue( ! lockfile.exists() );
        }
        final HubService hubService =
            new BasicHubService( new Random( 199099L ) );
        hubProf.start( new ClientProfile() {
            public HubConnection register() throws SampException {
                return hubService.register( "basic-test" );
            }
            public boolean isHubRunning() {
                return hubService.isHubRunning();
            }
        } );
        if ( lockfile != null ) {
            assertEquals( secret,
                          LockInfo.readLockFile( SampUtils
                                                .fileToUrl( lockfile ) )
                                  .getSecret() );
        }
        URL lockurl = hubProf.publishLockfile();
        assertEquals( secret, LockInfo.readLockFile( lockurl ).getSecret() );
        hubProf.stop();
        if ( lockfile != null ) {
            assertTrue( ! lockfile.exists() );
        }
        assertNull( LockInfo.readLockFile( lockurl ) );
    }
}
