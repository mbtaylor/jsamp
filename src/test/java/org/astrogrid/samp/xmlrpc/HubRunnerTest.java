package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.hub.BasicHubService;

public class HubRunnerTest extends TestCase {

    public HubRunnerTest() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( HttpServer.class.getName() )
              .setLevel( Level.SEVERE );
    }

    public void testRunHub() throws IOException {
        File tmpfile = File.createTempFile( "tmp", ".samp" );
        tmpfile.delete();
        runHub( tmpfile );
        runHub( null );
    }

    private void runHub( File lockfile ) throws IOException {
        final String secret = "its-a-secret";
        XmlRpcKit xmlrpc = XmlRpcKit.INTERNAL;
        Random random = new Random( 199099L );
        HubRunner runner = new HubRunner( xmlrpc.getClientFactory(),
                                          xmlrpc.getServerFactory(),
                                          new BasicHubService( random ),
                                          lockfile ) {
            public String createSecret() {
                return secret;
            }
        };
        if ( lockfile != null ) {
            assertTrue( ! lockfile.exists() );
        }
        runner.start();
        if ( lockfile != null ) {
            assertEquals( secret,
                          LockInfo.readLockFile( SampUtils
                                                .fileToUrl( lockfile ) )
                                  .getSecret() );
        }
        URL lockurl = runner.publishLockfile();
        assertEquals( secret, LockInfo.readLockFile( lockurl ).getSecret() );
        runner.shutdown();
        if ( lockfile != null ) {
            assertTrue( ! lockfile.exists() );
        }
        assertNull( LockInfo.readLockFile( lockurl ) );
    }
}
