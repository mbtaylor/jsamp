package org.astrogrid.samp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.TestCase;

public class SampUtilsTest extends TestCase {

    private final Random random_ = new Random( 232323L );

    public SampUtilsTest( String name ) {
        super( name );
    }

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
