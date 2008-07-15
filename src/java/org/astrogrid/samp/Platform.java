package org.astrogrid.samp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Platform-dependent features required by the SAMP implementation.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public abstract class Platform {

    private static Platform instance_;
    private final String name_;
    private static final Logger logger_ =
        Logger.getLogger( Platform.class.getName() );

    /**
     * Constructor.
     *
     * @param  name  platform name
     */
    protected Platform( String name ) {
        name_ = name;
    }

    /**
     * Returns SAMP's definition of the "home" directory.
     *
     * @return   directory containing SAMP lockfile
     */
    public abstract File getHomeDirectory(); 

    /**
     * Sets file permissions on a given file so that it cannot be read by
     * anyone other than its owner.
     * 
     * @param  file  file whose permissions are to be altered
     * @throws   IOException  if permissions cannot be changed
     */
    public void setPrivateRead( File file ) throws IOException {
        if ( setPrivateReadReflect( file ) ) {
            return;
        }
        else {
            exec( getPrivateReadArgs( file ) );
        }
    }

    /**
     * Returns an array of words to 
     * {@link java.lang.Runtime#exec(java.lang.String[])} in order
     * to set permissions on a given file so that it cannot be read by
     * anyone other than its owner.
     *
     * @param  file  file to alter
     * @return   exec args
     */
    protected abstract String[] getPrivateReadArgs( File file )
            throws IOException;

    /**
     * Attempt to use the <code>File.setReadable()</code> method to set
     * permissions on a file so that it cannot be read by anyone other
     * than its owner.
     *
     * @param  file  file to alter
     * @return   true  if the attempt succeeded, false if it failed because
     *           we are running the wrong version of java
     * @throws  IOException if there was some I/O failure
     */
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

    /**
     * Attempts a {@java.lang.Runtime#exec(java.lang.String[])} with a given
     * list of arguments.  The output from stdout is returned as a string;
     * in the case of error an IOException is thrown with a message giving
     * the output from stderr.
     *
     * <p><strong>Note:</strong> do not use this for cases in which the
     * output from stdout or stderr might be more than a few characters - 
     * blocking or deadlock is possible (see {@link java.lang.Process}).
     *
     * @param  args  array of words to pass to <code>exec</code>
     * @return  output from standard output
     * @throws  IOException  with text from standard error if there is an error
     */
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
                  new IOException( "Exec failed: " + argv ).initCause( e );
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

    /**
     * Slurps the contents of an input stream into a string.
     * The stream is closed.
     *
     * @param  in  input stream
     * @return  contents of <code>in</code>
     */
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

    /**
     * Returns a <code>Platform</code> instance for the current system.
     *
     * @return  platform instance
     */
    public static Platform getPlatform() {
        if ( instance_ == null ) {
            instance_ = createPlatform();
        }
        return instance_;
    }

    /**
     * Constructs a Platform for the current system.
     *
     * @return  new platform
     */
    private static Platform createPlatform() {

        // Is this reliable?
        String osname = System.getProperty( "os.name" );
        if ( osname.toLowerCase().startsWith( "windows" ) ||
             osname.toLowerCase().indexOf( "microsoft" ) >= 0 ) {
            return new WindowsPlatform();
        }
        else {
            return new UnixPlatform();
        }
    }

    /**
     * Platform implementation for Un*x-like systems.
     */
    private static class UnixPlatform extends Platform {

        /**
         * Constructor.
         */
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

    /**
     * Platform implementation for Microsoft Windows-like systems.
     */
    private static class WindowsPlatform extends Platform {

        /**
         * Constructor.
         */
        WindowsPlatform() {
            super( "MS Windows" );
        }

        protected String[] getPrivateReadArgs( File file ) throws IOException {

            // Thanks to Bruno Rino for this.
            return new String[] { "attrib", "-R", file.toString(), };
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
