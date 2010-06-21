package org.astrogrid.samp.hub;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.astrogrid.samp.Platform;

/**
 * Writes records to a SAMP Standard Profile hub lockfile.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class LockWriter {

    private final OutputStream out_;
    private static final byte[] linesep_ = getLineSeparator();
    private static final String TOKEN_REGEX = "[a-zA-Z0-9\\-_\\.]+";
    private static final Pattern TOKEN_PATTERN = Pattern.compile( TOKEN_REGEX );

    /**
     * Constructs a writer for writing to a given output stream.
     *
     * @param  out  output stream
     */
    public LockWriter( OutputStream out ) {
        out_ = out;
    }

    /**
     * Writes all the assignments in a given map to the lockfile.
     *
     * @param   map  assignment set to output
     */
    public void writeAssignments( Map map ) throws IOException {
        for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            writeAssignment( (String) entry.getKey(), 
                             (String) entry.getValue() );
        }
    }

    /**
     * Writes a single assignment to the lockfile.
     *
     * @param  name  assignment key
     * @param  value assignment value
     */
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

    /**
     * Writes a comment line to the lockfile.
     *
     * @param  comment  comment text
     */
    public void writeComment( String comment ) throws IOException {
        writeLine( "# " + comment );
    }

    /**
     * Writes a blank line to the lockfile.
     */
    public void writeLine() throws IOException {
        out_.write( linesep_ );
    }

    /**
     * Writes a line of text to the lockfile, terminated with a line-end.
     *
     * @param  line  line to write
     */
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

    /**
     * Closes the output stream.
     * May be required to ensure that all data is written.
     */
    public void close() throws IOException {
        out_.close();
    }

    /**
     * Sets the permissions on a given file suitably for a SAMP Standard
     * Profile lockfile.  This means that nobody apart from the file's
     * owner can read it.
     *
     * @param  file  file to set access permissions on
     */
    public static void setLockPermissions( File file ) throws IOException {
        Platform.getPlatform().setPrivateRead( file );
    }

    /**
     * Returns the platform-specific line separator sequence as an array of
     * bytes.
     *
     * @return  line separator sequence
     */
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
