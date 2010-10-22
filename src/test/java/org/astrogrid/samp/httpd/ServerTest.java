package org.astrogrid.samp.httpd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import junit.framework.TestCase;

public class ServerTest extends TestCase {

    public void testMultiMapperHandler() throws IOException {
        HttpServer server = new HttpServer();
        MultiURLMapperHandler mHandler =
            new MultiURLMapperHandler( server, "gur" );
        server.addHandler( mHandler );
        File f1 = new File( "a/b.fits" );
        URL fileUrl1 = f1.toURL();
        URL url1 = mHandler.addLocalUrl( f1.toURL() );
        assertEquals( "http", url1.getProtocol() );
        assertTrue( url1.toString().endsWith( "b.fits" ) );
        URL url2 =
            mHandler
           .addLocalUrl( ServerTest.class.getResource( "ServerTest.class" ) );
        assertEquals( "http", url2.getProtocol() );
        assertTrue( url2.toString().endsWith( ".class" ) );

        HttpServer.Request req2 =
            new HttpServer.Request( "GET", url2.getPath(), new HashMap(),
                                    new SocketAddress() {}, null );
        HttpServer.Response resp2 = server.serve( req2 );
        assertEquals( 200, resp2.getStatusCode() );
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        resp2.writeBody( bos );
        byte[] bosbuf = bos.toByteArray();
        assertEquals( (byte) 0xca, bosbuf[ 0 ] );
        assertEquals( (byte) 0xfe, bosbuf[ 1 ] );
        assertEquals( (byte) 0xba, bosbuf[ 2 ] );
        assertEquals( (byte) 0xbe, bosbuf[ 3 ] );

        mHandler.removeServerUrl( url2 );
        resp2 = server.serve( req2 );
        assertEquals( 404, resp2.getStatusCode() );

        assertNull( fileUrl1.getRef() );
        String frag = "frag";
        URL fragUrl1 = new URL( fileUrl1.toString() + "#" + frag ); 
        assertEquals( frag, fragUrl1.getRef() );
        assertEquals( frag, mHandler.addLocalUrl( fragUrl1 ).getRef() );
    }

    public void testHeaderMap() {
        HttpServer.HttpHeaderMap hdrMap = new HttpServer.HttpHeaderMap();
        hdrMap.addHeader( "a", "AA" );
        hdrMap.addHeader( "b", "BB" );
        hdrMap.addHeader( "c", "CC" );
        assertEquals( 3, hdrMap.size() );
        assertEquals( Arrays.asList( new String[] { "a", "b", "c" } ),
                      new ArrayList( hdrMap.keySet() ) );
        assertEquals( "CC", hdrMap.get( "c" ) );
        hdrMap.addHeader( "c", "DD" );
        assertEquals( Arrays.asList( new String[] { "a", "b", "c" } ),
                      new ArrayList( hdrMap.keySet() ) );
        assertEquals( "CC, DD", hdrMap.get( "c" ) );
        hdrMap.addHeader( "C", "EE" );
        assertEquals( Arrays.asList( new String[] { "a", "b", "c" } ),
                      new ArrayList( hdrMap.keySet() ) );
        assertEquals( "CC, DD, EE", hdrMap.get( "c" ) );

        assertEquals( "AA", HttpServer.getHeader( hdrMap, "a" ) );
        assertEquals( "AA", HttpServer.getHeader( hdrMap, "A" ) );
        assertNull( HttpServer.getHeader( hdrMap, "x" ) );
        assertNull( HttpServer.getHeader( hdrMap, null ) );
        assertEquals( "CC, DD, EE", HttpServer.getHeader( hdrMap, "c" ) );
        assertEquals( "CC, DD, EE", HttpServer.getHeader( hdrMap, "C" ) );
    }
}
