package org.astrogrid.samp.hub;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
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

    public static void setLockPermissions( File file ) throws IOException {
        try {
            Method setReadableMethod =
                File.class.getMethod( "setReadable",
                                      new Class[] { boolean.class,
                                                    boolean.class, } );
            boolean success =
                ( setReadableMethod.invoke( file,
                                          new Object[] { Boolean.FALSE,
                                                         Boolean.FALSE } )
                      .equals( Boolean.TRUE ) ) &&
                ( setReadableMethod.invoke( file,
                                            new Object[] { Boolean.TRUE,
                                                           Boolean.TRUE } )
                      .equals( Boolean.TRUE ) );
            if ( success ) { 
                return;
            }
            else {
                throw new IOException( "Operation disallowed" );
            } 
        }
        catch ( InvocationTargetException e1 ) {
            Throwable e2 = e1.getCause();
            if ( e2 instanceof IOException ) {
                throw (IOException) e2;
            }
            else if ( e2 instanceof RuntimeException ) {
                throw (RuntimeException) e2;
            }
            else {
                throw (IOException) new IOException( e2.getMessage() )
                                   .initCause( e2 );
            }
        }
        catch ( NoSuchMethodException e ) {
            // method only available at java 1.6+
        }
        catch ( IllegalAccessException e ) {
            // not likely.
        }

        // Got here - not chmodded yet.
        String[] args;
        if ( SampUtils.isWindows() ) {
  throw new IOException( "Anyone know right command for windows?" );
        }
        else {
            args = new String[] { "chmod", "600", file.toString(), };
        }
        Process process;
        try {
            process = Runtime.getRuntime().exec( args );
            process.waitFor();
        }
        catch ( InterruptedException e ) {
            throw new IOException( "Exec failed: " + Arrays.asList( args ) );
        }
        catch ( IOException e ) {
            throw (IOException)
                  new IOException( "Exec failed: " + Arrays.asList( args ) )
                 .initCause( e );
        }
        if ( process.exitValue() == 0 ) {
            return;
        }
        else {
            String err;
            try {
                InputStream es = process.getErrorStream();
                StringBuffer sbuf = new StringBuffer();
                for ( int c; ( c = es.read() ) >= 0; ) {
                    sbuf.append( (char) c );
                }
                es.close();
                err = sbuf.toString();
            }
            catch ( IOException e ) {
                err = "??";
            }
            throw new IOException( "Exec failed: " + Arrays.asList( args )
                                 + " - " + err );
        }
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
