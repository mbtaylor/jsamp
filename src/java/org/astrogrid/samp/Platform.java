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
     * Returns the value of an environment variable.
     * If it can't be done, null is returned.
     *
     * @param  varname  name of environment variable
     * @return value of environment variable
     */
    public String getEnv( String varname ) {
        try {
            return System.getenv( varname );
        }

        // System.getenv is unimplemented at 1.4, and throws an Error.
        catch ( Throwable e ) {
            String[] argv = getGetenvArgs( varname );
            if ( argv == null ) {
                return null;
            }
            else {
                try {
                    String cmdout = exec( argv );
                    return cmdout.trim();
                }
                catch ( Throwable e2 ) {
                    return null;
                }
            }
        }
    }

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
            String[] privateReadArgs = getPrivateReadArgs( file );
            if ( privateReadArgs != null ) {
                exec( privateReadArgs );
            }
            else {
                logger_.info( "No known way to set user-only read permissions"
                            + "; possible security implications"
                            + " on multi-user systems" );
            }
        }
    }

    /**
     * Returns an array of words to pass to
     * {@link java.lang.Runtime#exec(java.lang.String[])} in order
     * to read an environment variable name.
     * If null is returned, no way is known to do this with a system command.
     *
     * @param  varname  environment variable name to read
     * @return  exec args
     */
    protected abstract String[] getGetenvArgs( String varname );

    /**
     * Returns an array of words to pass to
     * {@link java.lang.Runtime#exec(java.lang.String[])} in order
     * to set permissions on a given file so that it cannot be read by
     * anyone other than its owner.
     * If null is returned, no way is known to do this with a system command.
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
            return success;
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
        final Process process;
        final StreamReader outReader;
        final StreamReader errReader;
        try {
            process = Runtime.getRuntime().exec( args );
            outReader = new StreamReader( process.getInputStream() );
            errReader = new StreamReader( process.getErrorStream() );
            outReader.start();
            errReader.start();
            process.waitFor();
        }
        catch ( InterruptedException e ) {
            throw new IOException( "Exec failed: " + argv );
        }
        catch ( IOException e ) {
            throw (IOException)
                  new IOException( "Exec failed: " + argv ).initCause( e );
        }
        return process.exitValue() == 0 ? outReader.getContent()
                                        : errReader.getContent();
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
     * Thread which reads the contents of a stream into a string buffer.
     */
    private static class StreamReader extends Thread {
        private final InputStream in_;
        private final StringBuffer sbuf_;

        /**
         * Constructor.
         *
         * @param  in  input stream
         */
        StreamReader( InputStream in ) {
            super( "StreamReader" );
            in_ = in;
            sbuf_ = new StringBuffer();
            setDaemon( true );
        }

        public void run() {
            try {
                for ( int c; ( c = in_.read() ) >= 0; ) {
                    sbuf_.append( (char) c );
                }
                in_.close();
            }
            catch ( IOException e ) {
            }
        }

        /**
         * Returns the content of the stream.
         *
         * @return content
         */
        public String getContent() {
            return sbuf_.toString();
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

        protected String[] getGetenvArgs( String varname ) {
            return new String[] { "printenv", varname, };
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

            // No good way known.  For a while I was using "attrib -R file", 
            // but this wasn't doing what was wanted.  Bruno Rino has
            // suggested "CALCS file /G %USERNAME%:F".  Sounds kind of
            // sensible, but requires user input (doable, but fiddly), 
            // and from my experiments on NTFS doesn't seem to have any 
            // discernable effect.  As I understand it, it's unlikely to do
            // anything on FAT (no ACLs).  Given my general ignorance of 
            // MS OSes and file systems, I'm inclined to leave this for 
            // fear of inadvertently doing something bad.
            return null;
        }

        public File getHomeDirectory() {
            String userprofile = getEnv( "USERPROFILE" );
            if ( userprofile != null && userprofile.trim().length() > 0 ) {
                return new File( userprofile );
            }
            else {
                return new File( System.getProperty( "user.home" ) );
            }
        }

        public String[] getGetenvArgs( String varname ) {
            return new String[] { "cmd", "/c", "echo", "%" + varname + "%", };
        }
    }
}
