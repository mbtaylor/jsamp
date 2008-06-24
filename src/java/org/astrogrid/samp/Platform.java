package org.astrogrid.samp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Logger;

public abstract class Platform {

    private static Platform instance_;
    private final String name_;
    private static final Logger logger_ =
        Logger.getLogger( Platform.class.getName() );

    protected Platform( String name ) {
        name_ = name;
    }

    public abstract File getHomeDirectory(); 

    protected abstract String[] getPrivateReadArgs( File file )
            throws IOException;

    public void setPrivateRead( File file ) throws IOException {
        if ( setPrivateReadReflect( file ) ) {
            return;
        }
        else {
            exec( getPrivateReadArgs( file ) );
        }
    }

    private static boolean setPrivateReadReflect( File file )
            throws IOException {
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
                return true;
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
            return false;
        }
        catch ( IllegalAccessException e ) {
            // not likely.
            return false;
        }
    }

    private static String exec( String[] args ) throws IOException {
        String argv = Arrays.asList( args ).toString();
        logger_.info( "System exec: " + argv );
        Process process;
        try {
            process = Runtime.getRuntime().exec( args );
            process.waitFor();
        }
        catch ( InterruptedException e ) {
            throw new IOException( "Exec failed: " + argv );
        }
        catch ( IOException e ) {
            throw (IOException)
                  new IOException( "Exec faile: " + argv ).initCause( e );
        }
        if ( process.exitValue() == 0 ) {
            return readStream( process.getInputStream() );
        }
        else {
            String err;
            try {
                err = readStream( process.getErrorStream() );
            }
            catch ( IOException e ) {
                err = "??";
            }
            throw new IOException( "Exec failed: " + argv + " - " + err );
        }
    }

    private static String readStream( InputStream in ) throws IOException {
        try {
            StringBuffer sbuf = new StringBuffer();
            for ( int c; ( c = in.read() ) >= 0; ) {
                sbuf.append( (char) c );
            }
            return sbuf.toString();
        }
        finally {
            try {
                in.close();
            }
            catch ( IOException e ) {
            }
        }
    }

    public static Platform getPlatform() {
        if ( instance_ == null ) {
            instance_ = createPlatform();
        }
        return instance_;
    }

    private static Platform createPlatform() {
        String osname = System.getProperty( "os.name" );
        if ( osname.toLowerCase().startsWith( "windows" ) ||
             osname.toLowerCase().indexOf( "microsoft" ) >= 0 ) {
            return new WindowsPlatform();
        }
        else {
            return new UnixPlatform();
        }
    }

    private static class UnixPlatform extends Platform {

        UnixPlatform() {
            super( "Un*x" );
        }

        public File getHomeDirectory() {
            return new File( System.getProperty( "user.home" ) );
        }

        protected String[] getPrivateReadArgs( File file ) {
            return new String[] { "chmod", "600", file.toString(), };
        }
    }

    private static class WindowsPlatform extends Platform {

        WindowsPlatform() {
            super( "MS Windows" );
        }

        protected String[] getPrivateReadArgs( File file ) throws IOException {
  throw new IOException( "Anyone know right command for windows?" );
        }

        public File getHomeDirectory() {
            String userprofile = null;
            try {
                userprofile = System.getenv( "USERPROFILE" );
            }

            // System.getenv is unimplemented at 1.4, and throws an Error.
            catch ( Throwable e ) {
                try {
                    String[] argv = { "cmd", "/c", "echo", "%USERPROFILE%", };
                    return new File( exec( argv ) );
                }
                catch ( Throwable e2 ) {
                    userprofile = null;
                }
            }
            if ( userprofile != null && userprofile.trim().length() > 0 ) {
                return new File( userprofile );
            }
            else {
                return new File( System.getProperty( "user.home" ) );
            }
        }
    }
}
