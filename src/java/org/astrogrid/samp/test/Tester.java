package org.astrogrid.samp.test;

/**
 * No-frills test case superclass.
 *
 * @author   Mark Taylor
 * @since    18 Jul 2008
 */
public class Tester {

    /**
     * Fails a test.
     *
     * @throws TextException  always
     */
    public static void fail() throws TestException {
        throw new TestException( "Test failed" );
    }

    /**
     * Tests an assertion.
     *
     * @param   test   asserted condition
     * @throws  TestException  if <code>test</code>  is false
     */
    public static void assertTrue( boolean test ) throws TestException {
        if ( ! test ) {
            throw new TestException( "Test failed" );
        }
    }

    /**
     * Tests object equality.
     *
     * @param   o1  object 1
     * @param   o2  object 2
     * @throws  TestException  unless <code>o1</code> and <code>o2</code>
     *          are both <code>null</code> or are equal in the sense of
     *          {@link java.lang.Object#equals}
     */
    public static void assertEquals( Object o1, Object o2 )
            throws TestException {
        if ( o1 == null && o2 == null ) {
        }
        else if ( o1 == null || ! o1.equals( o2 ) ) {
            throw new TestException(
                "Test failed: " +  o1 + " != " + o2 );
        }
    }

    /**
     * Tests integer equality.
     *
     * @param  i1  integer 1
     * @param  i2  integer 2
     * @throws  TestException  iff <code>i1</code> != <code>i2</code>
     */
    public static void assertEquals( int i1, int i2 ) throws TestException {
        if ( i1 != i2 ) {
            throw new TestException(
                "Test failed: " + i1 + " != " + i2 );
        }
    }
}
