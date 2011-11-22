package org.astrogrid.samp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import junit.framework.TestCase;

public class SampUtilsTest extends TestCase {

    private final Random random_ = new Random( 232323L );

    public void testCodecs() {
        for ( int k = 0; k < 100; k++ ) {
            boolean b = random_.nextBoolean();
            int i = random_.nextInt();
            long l = random_.nextLong();
            double f = random_.nextDouble();
            assertEquals( b, SampUtils
                            .decodeBoolean( SampUtils.encodeBoolean( b ) ) );
            assertEquals( i, SampUtils
                            .decodeInt( SampUtils.encodeInt( i ) ) );
            assertEquals( l, SampUtils
                            .decodeLong( SampUtils.encodeLong( l ) ) );
            assertEquals( f, SampUtils
                            .decodeFloat( SampUtils.encodeFloat( f ) ), 0.0 );
        }
    }

    public void testUriCodecs() {
        assertEquals( "abc", SampUtils.uriEncode( "abc" ) );
        assertEquals( "abc", SampUtils.uriDecode( "abc" ) );
        assertEquals( "a b+c+d", SampUtils.uriDecode( "a%20b+c%2Bd" ) );
        String[] txts = {
            "abc",
            "http://localhost/file",
            "a b c * / % ++--%1",
        };
        for ( int i = 0; i < txts.length; i++ ) {
            String txt = txts[ i ];
            assertEquals( txt,
                          SampUtils.uriDecode( SampUtils.uriEncode( txt ) ) );
            assertEquals( txt,
                          SampUtils.uriDecode(
                              SampUtils.uriDecode(
                                  SampUtils.uriEncode(
                                      SampUtils.uriEncode( txt ) ) ) ) );
        }
    }

    public void testChars() {
        assertTrue( SampUtils.isStringChar( 'x' ) );
        assertTrue( SampUtils.isStringChar( 'X' ) );
        assertTrue( SampUtils.isStringChar( (char) 0x09 ) );
        assertTrue( SampUtils.isStringChar( (char) 0x0a ) );
        assertTrue( SampUtils.isStringChar( (char) 0x0d ) );

        assertTrue( ! SampUtils.isStringChar( (char) 0 ) );
        assertTrue( ! SampUtils.isStringChar( (char) 0x0e ) );
        assertTrue( ! SampUtils.isStringChar( '\b' ) );
        assertTrue( ! SampUtils.isStringChar( (char) 0xffef ) );
    }

    public void testChecks() {
        goodObject( "a" );
        goodObject( "abc\t\r\n" );
        badObject( "abc\b" );
        badObject( new Integer( 23 ) );
        badObject( new Double( Math.E ) );
        badObject( null );
        badObject( Arrays.asList( new Object[] { "AA", null, } ) );
        List abclist = Arrays.asList( new Object[] { "a", "bb", "ccc" } );
        goodObject( abclist );
        goodObject( new ArrayList() );
        goodObject( new HashMap() );
        Map xmap = new HashMap();
        xmap.put( "xx", abclist );
        goodObject( xmap );
        xmap.put( abclist, "xx" );
        badObject( xmap );
    }

    public void testHostname() throws UnknownHostException {
        String hprop = SampUtils.LOCALHOST_PROP;
        Properties sysprops = System.getProperties();
        String prop = sysprops.getProperty( hprop );
        sysprops.remove( hprop );
        assertEquals( "127.0.0.1", SampUtils.getLocalhost() );
        sysprops.setProperty( hprop, "host-with-the-most" );
        assertEquals( "host-with-the-most", SampUtils.getLocalhost() );
        sysprops.setProperty( hprop, "[hostname]" );
        assertEquals( InetAddress.getLocalHost().getCanonicalHostName(),
                      SampUtils.getLocalhost() );
        if ( prop == null ) {
            sysprops.remove( hprop );
        }
        else {
            sysprops.setProperty( hprop, prop );
        }
    }

    public void testJson() {
        Map m = new LinkedHashMap();
        m.put( "one", "1" );
        m.put( "two", "2" );
        m.put( "list", Arrays.asList( new String[] { "A", "B", "C" } ) );
        String js =
            "{\"one\":\"1\",\"two\":\"2\",\"list\":[\"A\",\"B\",\"C\"]}";
        assertEquals( nows( js ), nows( SampUtils.toJson( m, false ) ) );
        assertEquals( nows( js ), nows( SampUtils.toJson( m, true ) ) );
        assertEquals( nows( js ),
                      nows( SampUtils.toJson( SampUtils.fromJson( js ),
                                              true ) ) );
        assertTrue( SampUtils.toJson( m, false ).indexOf( '\n' ) < 0 );
        assertTrue( SampUtils.toJson( m, true ).indexOf( '\n' ) >= 0 );
        assertEquals( new HashMap(), SampUtils.fromJson("{}") );
        assertEquals( new HashMap(), SampUtils.fromJson("{ }") );
        assertEquals( new ArrayList(), SampUtils.fromJson("[]") );
        assertEquals( new ArrayList(), SampUtils.fromJson("[ ]") );
    }

    private static String nows( String txt ) {
        return txt.replaceAll( "\\s+", "" );
    }

    private void goodObject( Object obj ) {
        SampUtils.checkObject( obj );
        SampUtils.checkList( Arrays.asList( new Object[] { "a", obj, "c" } ) );
        Map map = new HashMap();
        map.put( "k1", "v1" );
        map.put( "key", obj );
        map.put( "k2", "v2" );
        SampUtils.checkMap( map );
    }

    private void badObject( Object obj ) {
        try {
            SampUtils.checkObject( obj );
            fail( "Object should be bad: " + obj );
        }
        catch ( DataException e ) {
            return;
        }
        try {
            SampUtils.checkList( Arrays.asList( new Object[]
                                                { "a", obj, "c" } ) );
            fail( "Object should be bad: " + obj );
        }
        catch ( DataException e ) {
            return;
        }
    }
}
