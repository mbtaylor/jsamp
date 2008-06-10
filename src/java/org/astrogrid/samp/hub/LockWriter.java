package org.astrogrid.samp.hub;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.astrogrid.samp.SampUtils;

public class LockWriter {

    private final OutputStream out_;
    private static final byte[] linesep_ = getLineSeparator();
    private static final String TOKEN_REGEX = "[a-zA-Z0-9\\-_\\.]+";
    private static final Pattern TOKEN_PATTERN = Pattern.compile( TOKEN_REGEX );

    public LockWriter() throws IOException {
        this( new FileOutputStream( SampUtils.getLockFile() ) );
    }

    public LockWriter( OutputStream out ) {
        out_ = out;
    }

    public void writeAssignments( Map map ) throws IOException {
        for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            writeAssignment( (String) entry.getKey(), 
                             (String) entry.getValue() );
        }
    }

    public void writeAssignment( String name, String value )
            throws IOException {
        if ( TOKEN_PATTERN.matcher( name ).matches() ) {
            writeLine( name + "=" + value );
        }
        else {
            throw new IllegalArgumentException( "Bad name sequence: " + name
                                              + " !~" + TOKEN_REGEX );
        }
    }

    public void writeComment( String comment ) throws IOException {
        writeLine( "# " + comment );
    }

    public void writeLine() throws IOException {
        out_.write( linesep_ );
    }

    protected void writeLine( String line ) throws IOException {
        byte[] bbuf = new byte[ line.length() ];
        for ( int i = 0; i < line.length(); i++ ) {
            int c = line.charAt( i );
            if ( c < 0x20 || c > 0x7f ) {
                throw new IllegalArgumentException( "Illegal character 0x" +
                                                    Integer.toHexString( c ) );
            }
            bbuf[ i ] = (byte) c;
        }
        out_.write( bbuf );
        writeLine();
    }

    public void close() throws IOException {
        out_.close();
    }

    private static final byte[] getLineSeparator() {
        String linesep = System.getProperty( "line.separator" );
        if ( linesep.matches( "[\\r\\n]+" ) ) {
            byte[] lsbuf = new byte[ linesep.length() ];
            for ( int i = 0; i < linesep.length(); i++ ) {
                lsbuf[ i ] = (byte) linesep.charAt( i );
            }
            return lsbuf;
        }
        else {
            return new byte[] { (byte) '\n' };
        }
    }
}
