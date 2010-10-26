package org.astrogrid.samp.hub;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Object which can generate a sequence of private keys.
 * The values returned by the next() method should in general not be
 * easy to guess.
 *
 * @author   Mark Taylor
 * @since    26 Oct 2010
 */
public class KeyGenerator {

    private final String prefix_;
    private final int nchar_;
    private final Random random_;
    private int iseq_;
    private static final char SEQ_DELIM = '_';

    /**
     * Constructor.
     *
     * @param  prefix  prefix prepended to all generated keys
     * @param  nchar   number of characters in generated keys
     * @param  random  random number generator
     */
    public KeyGenerator( String prefix, int nchar, Random random ) {
        prefix_ = prefix;
        nchar_ = nchar;
        random_ = random;
    }

    /**
     * Returns the next key in the sequence.
     * Guaranteed different from any previous return value from this method.
     *
     * @return  key string
     */
    public synchronized String next() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( prefix_ );
        sbuf.append( Integer.toString( ++iseq_ ) );
        sbuf.append( SEQ_DELIM );
        for ( int i = 0; i < nchar_; i++ ) {
            char c = (char) ( 'a' + (char) random_.nextInt( 'z' - 'a' ) );
            assert c != SEQ_DELIM;
            sbuf.append( c );
        }
        return sbuf.toString();
    }

    /**
     * Returns a new, randomly seeded, Random object.
     *
     * @return  random
     */
    public static Random createRandom() {
        byte[] seedBytes = new SecureRandom().generateSeed( 8 );
        long seed = 0L;
        for ( int i = 0; i < 8; i++ ) {
            seed = ( seed << 8 ) | ( seedBytes[ i ] & 0xff );
        }
        return new Random( seed );
    }
}
