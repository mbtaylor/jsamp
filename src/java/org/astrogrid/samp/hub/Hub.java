package org.astrogrid.samp.hub;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.UtilServer;
import org.astrogrid.samp.xmlrpc.StandardHubProfile;
import org.astrogrid.samp.xmlrpc.StandardHubProfileFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Class which starts and stops a hub and its associated profiles.
 * Static methods are provided for starting a hub in the current or an
 * external JVM, and a <code>main()</code> method is provided for
 * use from the command line.
 *
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public class Hub {

    private final HubService service_;
    private final HubProfile[] profiles_;
    private static Class[] defaultProfileClasses_ = new Class[] {
        StandardHubProfile.class,
    };
    private static final Logger logger_ =
        Logger.getLogger( Hub.class.getName() );

    /**
     * Constructor.
     *
     * @param   service   hub service
     * @param   profiles   hub profiles for access from clients
     */
    public Hub( HubService service, HubProfile[] profiles ) {
        service_ = service;
        profiles_ = profiles == null ? createDefaultProfiles() : profiles;
    }

    /**
     * Starts this hub and its profiles running.
     */
    public void start() throws IOException {
        logger_.info( "Starting hub service" );
        service_.start();
        try {
            for ( int i = 0; i < profiles_.length; i++ ) {
                logger_.info( "Starting hub profile " + profiles_[ i ] );
                profiles_[ i ].start( service_ );
            }
        }
        catch ( IOException e ) {
            try {
                shutdown();
            }
            finally {
                throw e;
            }
        }
    }

    /**
     * Stops this hub and its profiles running.
     */
    public void shutdown() {
        service_.shutdown();
        for ( int i = 0; i < profiles_.length; i++ ) {
            try {
                profiles_[ i ].shutdown();
            }
            catch ( IOException e ) {
            }
        }
    }

    /**
     * Returns a standard list of known HubProfileFactories.
     * This is used by the {@link #main} method.
     *
     * @return   array of known hub profile factories
     */
    public static HubProfileFactory[] getKnownHubProfileFactories() {
        return new HubProfileFactory[] {
            new StandardHubProfileFactory(),
        };
    }

    /**
     * Returns a copy of the default set of HubProfile classes used
     * when a hub is run and the list of profiles is not set explicitly.
     * Each element should be an implementation of {@link HubProfile}
     * with a no-arg constructor.
     *
     * @return   array of hub profile classes
     */
    public static Class[] getDefaultProfileClasses() {
        return (Class[]) defaultProfileClasses_.clone();
    }

    /**
     * Sets the default set of HubProfile classes.
     *
     * @param  dfltProfileClasses  array to be returned by
     *         getDefaultProfileClasses
     */
    public static void setDefaultProfileClasses( Class[] dfltProfileClasses ) {
        for ( int ip = 0; ip < dfltProfileClasses.length; ip++ ) {
            Class clazz = dfltProfileClasses[ ip ];
            if ( ! HubProfile.class.isAssignableFrom( clazz ) ) {
                throw new IllegalArgumentException( "Class " + clazz.getName()
                                                  + " not a HubProfile" );
            }
        }
        defaultProfileClasses_ = (Class[]) dfltProfileClasses.clone();
    }

    /**
     * Returns an array of default Hub Profiles.
     * This is the result of calling the no-arg constructor
     * for each element of the result of {@link #getDefaultProfileClasses}.
     *
     * @return   array of hub profiles to use by default
     */
    public static HubProfile[] createDefaultProfiles() {
        List hubProfileList = new ArrayList();
        for ( int ip = 0; ip < defaultProfileClasses_.length; ip++ ) {
            Class clazz = defaultProfileClasses_[ ip ];
            try {
                hubProfileList.add( (HubProfile) clazz.newInstance() );
            }
            catch ( ClassCastException e ) {
                logger_.warning( "No hub profile " + clazz.getName()
                               + " - not a " + HubProfile.class.getName() );
            }
            catch ( InstantiationException e ) {
                logger_.warning( "No hub profile " + clazz.getName()
                               + " - failed to instantiate (" + e + ")" );
            }
            catch ( IllegalAccessException e ) {
                logger_.warning( "No hub profile " + clazz.getName()
                               + " - inaccessible constructor (" + e + ")" );
            }
        }
        return (HubProfile[]) hubProfileList.toArray( new HubProfile[ 0 ] );
    }

    /**
     * Starts a SAMP hub with a given set of profiles.
     * The returned hub is running (<code>start</code> has been called).
     *
     * <p>If the hub mode corresponds to one of the GUI options,
     * one of two things will happen.  An attempt will be made to install
     * an icon in the "system tray"; if this is successful, the attached
     * popup menu will provide options for displaying the hub window and
     * for shutting it down.  If no system tray is available, the hub window
     * will be posted directly, and the hub will shut down when this window
     * is closed.  System tray functionality is only available when running
     * under Java 1.6 or later, and when using a suitable display manager.
     *
     * @param   hubMode  hub mode
     * @param   profiles   SAMP profiles to support; if null a default set
     *                     will be used
     * @return  running hub
     */
    public static Hub runHub( HubServiceMode hubMode, HubProfile[] profiles )
            throws IOException {
        final Hub[] runners = new Hub[ 1 ];
        HubService hubService =
            hubMode.createHubService( KeyGenerator.createRandom(), runners );
        final Hub hub = new Hub( hubService, profiles );
        runners[ 0 ] = hub;
        Runtime.getRuntime().addShutdownHook( new Thread( "Hub Terminator" ) {
            public void run() {
                hub.shutdown();
            }
        } );
        boolean started = false;;
        try {
            hub.start();
            started = true;
            return hub;
        }
        finally {
            if ( ! started ) {
                hub.shutdown();
            }
        }
    }

    /**
     * Starts a SAMP hub with a default set of profiles.
     * This convenience method invokes <code>runHub(hubMode,null)</code>.
     *
     * @param   hubMode  hub mode
     * @return  running hub
     * @see    #runHub(HubServiceMode,HubProfile[])
     */
    public static Hub runHub( HubServiceMode hubMode ) throws IOException {
        return runHub( hubMode, null );
    }

    /**
     * Attempts to start a hub in a new JVM with a given set
     * of profiles.  The resulting hub can therefore outlast the
     * lifetime of the current application.
     * Because of the OS interaction required, it's hard to make this
     * bulletproof, and it may fail without an exception, but we do our best.
     *
     * @param   hubMode  hub mode
     * @param   profileClasses  classes which implement {@link HubProfile}
     *          and have a no-arg constructor, determining which profiles
     *          the hub will support;  if null, a default set is used
     * @see     #checkExternalHubAvailability
     */
    public static void runExternalHub( HubServiceMode hubMode,
                                       Class[] profileClasses )
            throws IOException {
        String classpath = System.getProperty( "java.class.path" );
        if ( classpath == null || classpath.trim().length() == 0 ) {
            throw new IOException( "No classpath available - JNLP context?" );
        }
        File javaHome = new File( System.getProperty( "java.home" ) );
        File javaExec = new File( new File( javaHome, "bin" ), "java" );
        String javacmd = ( javaExec.exists() && ! javaExec.isDirectory() )
                       ? javaExec.toString()
                       : "java";
        String[] propagateProps = new String[] {
            XmlRpcKit.IMPL_PROP,
            UtilServer.PORT_PROP,
            SampUtils.LOCALHOST_PROP,
            "java.awt.Window.locationByPlatform",
        };
        List argList = new ArrayList();
        argList.add( javacmd );
        for ( int ip = 0; ip < propagateProps.length; ip++ ) {
            String propName = propagateProps[ ip ];
            String propVal = System.getProperty( propName );
            if ( propVal != null ) {
                argList.add( "-D" + propName + "=" + propVal );
            }
        }
        argList.add( "-classpath" );
        argList.add( classpath );
        argList.add( Hub.class.getName() );
        argList.add( "-mode" );
        argList.add( hubMode.toString() );
        if ( profileClasses != null ) {
            profileClasses = getDefaultProfileClasses();
            for ( int ip = 0; ip < profileClasses.length; ip++ ) {
                argList.add( "-profile" );
                argList.add( profileClasses[ ip ].getName() );
            }
        }
        String[] args = (String[]) argList.toArray( new String[ 0 ] );
        StringBuffer cmdbuf = new StringBuffer();
        for ( int iarg = 0; iarg < args.length; iarg++ ) {
            if ( iarg > 0 ) {
                cmdbuf.append( ' ' );
            }
            cmdbuf.append( args[ iarg ] );
        }
        logger_.info( "Starting external hub" );
        logger_.info( cmdbuf.toString() );
        execBackground( args );
    }

    /**
     * Attempts to run a hub in a new JVM with a default set of profiles.
     * The default set is taken from that in this JVM.
     * This convenience method invokes 
     * <code>runExternalHub(hubMode,null)</code>.
     *
     * @param   hubMode  hub mode
     * @see     #runExternalHub(HubServiceMode,java.lang.Class[])
     */
    public static void runExternalHub( HubServiceMode hubMode )
            throws IOException {
        runExternalHub( hubMode, null );
    }

    /**
     * Attempts to determine whether an external hub can be started using
     * {@link #runExternalHub runExternalHub}.
     * If it can be determined that such an
     * attempt would fail, this method will throw an exception with
     * an informative message.  This method succeeding is not a guarantee
     * that an external hub can be started successfullly.
     * The behaviour of this method is not expected to change over the
     * lifetime of a given JVM.
     */
    public static void checkExternalHubAvailability() throws IOException {
        String classpath = System.getProperty( "java.class.path" );
        if ( classpath == null || classpath.trim().length() == 0 ) {
            throw new IOException( "No classpath available - JNLP context?" );
        }
        if ( System.getProperty( "jnlpx.jvm" ) != null ) {
            throw new IOException( "Running under WebStart"
                                 + " - external hub not likely to work" );
        }
    }
    
    /**
     * Main method, which allows configuration of which profiles will run
     * and configuration of those individual profiles.
     * Use the <code>-h</code> flag for usage.
     */
    public static void main( String[] args ) {
        try {
            int status = runMain( args );
            if ( status != 0 ) {
                System.exit( status );
            }
        }

        // Explicit exit on error may be necessary to kill Swing.
        catch ( Throwable e ) {
            e.printStackTrace();
            System.exit( 2 );
        }
    }

    /**
     * Invoked by main.
     * In case of a usage error, it returns a non-zero value, but does not
     * call System.exit.
     *
     * @param   args  command-line argument array
     * @return  non-zero for error completion
     */
    public static int runMain( String[] args ) throws IOException {
        HubProfileFactory[] knownProfileFactories =
            getKnownHubProfileFactories();

        // Assemble usage message.
        StringBuffer ubuf = new StringBuffer();
        ubuf.append( "\n   Usage:" )
            .append( "\n      " )
            .append( Hub.class.getName() )
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n           " )
            .append( " [-mode " );
        HubServiceMode[] modes = HubServiceMode.getAvailableModes();
        for ( int im = 0; im < modes.length; im++ ) {
            if ( im > 0 ) {
                ubuf.append( '|' );
            }
            ubuf.append( modes[ im ].getName() );
        }
        ubuf.append( ']' )
            .append( "\n           " )
            .append( " [-profile " );
        for ( int ip = 0; ip < knownProfileFactories.length; ip++ ) {
            ubuf.append( knownProfileFactories[ ip ].getName() )
                .append( '|' );
        }
        ubuf.append( "<hubprofile-class>]" )
            .append( " ..." );
        for ( int ip = 0; ip < knownProfileFactories.length; ip++ ) {
            List pusageList =
                 new ArrayList( Arrays.asList( knownProfileFactories[ ip ]
                                              .getFlagsUsage() ) );
            while ( ! pusageList.isEmpty() ) {
                StringBuffer sbuf = new StringBuffer()
                            .append( "\n           " );
                for ( Iterator it = pusageList.iterator(); it.hasNext(); ) {
                    String pusage = (String) it.next();
                    if ( sbuf.length() + pusage.length() < 78 ) {
                        sbuf.append( ' ' )
                            .append( pusage );
                        it.remove();
                    }
                    else {
                        break;
                    }
                }
                ubuf.append( sbuf );
            }
        }
        ubuf.append( '\n' );
        String usage = ubuf.toString();

        // Get default hub mode.
        HubServiceMode hubMode = HubServiceMode.MESSAGE_GUI;
        if ( ! Arrays.asList( HubServiceMode.getAvailableModes() )
                     .contains( hubMode ) ) {
            hubMode = HubServiceMode.NO_GUI;
        }

        // Parse general command-line arguments.
        List argList = new ArrayList( Arrays.asList( args ) );
        int verbAdjust = 0;
        String stdSecret = null;
        boolean stdHttplock = false;
        String webAuth = "swing";
        String webLog = "none";
        boolean webRemote = false;
        List profNameList = new ArrayList();
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-mode" ) && it.hasNext() ) {
                it.remove();
                String mode = (String) it.next();
                it.remove();
                hubMode = HubServiceMode.getModeFromName( mode );
                if ( hubMode == null ) {
                    System.err.println( "Unkown mode " + mode );
                    System.err.println( usage );
                    return 1;
                }
            }
            else if ( ( arg.equals( "-profile" ) || arg.equals( "-prof" ) )
                      && it.hasNext() ) {
                it.remove();
                profNameList.add( (String) it.next() );
                it.remove();
            }
            else if ( arg.equals( "-v" ) || arg.equals( "-verbose" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.equals( "+v" ) || arg.equals( "+verbose" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.equals( "-h" ) || arg.equals( "-help" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
        }

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Assemble list of profile factories to use.
        if ( profNameList.isEmpty() ) {
            profNameList.add( "std" );
        }

        // Assemble list of profiles to use.
        List profileList = new ArrayList();
        for ( Iterator it = profNameList.iterator(); it.hasNext(); ) {
            String profName = (String) it.next();
            HubProfileFactory profFactory = null;
            for ( int ip = 0;
                  ip < knownProfileFactories.length && profFactory == null;
                  ip++ ) {
                HubProfileFactory pf = knownProfileFactories[ ip ];
                if ( profName.equals( pf.getName() ) ||
                     profName.equals( pf.getClass().getName() ) ) {
                    profFactory = pf;
                }
            }
            if ( profFactory == null ) {
                final Class clazz;
                try {
                    clazz = Class.forName( profName );
                }
                catch ( ClassNotFoundException e ) {
                    System.err.println( "Unknown profile " + profName + "\n"
                                      + usage );
                    return 1;
                }
                if ( HubProfile.class.isAssignableFrom( clazz ) ) {
                    try {
                        profileList.add( (HubProfile) clazz.newInstance() );
                    }
                    catch ( InstantiationException e ) {
                        System.err.println( "Error instantiating HubProfile "
                                          + clazz + ": " + e );
                    }
                    catch ( IllegalAccessException e ) {
                        System.err.println( "Error instantiating HubProfile "
                                          + clazz + ": " + e );
                    }
                }
            }
            else {
                try {
                    profileList.add( profFactory.createHubProfile( argList ) );
                }
                catch ( RuntimeException e ) {
                    System.err.println( "Error configuring profile " + profName
                                      + ":\n" + e.getMessage() );
                    return 1;
                }
            }
        }
        HubProfile[] profiles =
            (HubProfile[]) profileList.toArray( new HubProfile[ 0 ] );

        // Check all command line args have been used.
        if ( ! argList.isEmpty() ) {
            System.err.println( "Some args not used " + argList );
            System.err.println( usage );
            return 1;
        }

        // Start hub service and install profile-specific interfaces.
        runHub( hubMode, profiles );

        // For non-GUI case block indefinitely otherwise the hub (which uses
        // a daemon thread) will just exit immediately.
        if ( hubMode.isDaemon() ) {
            Object lock = new String( "Indefinite" );
            synchronized ( lock ) {
                try {
                    lock.wait();
                }
                catch ( InterruptedException e ) {
                }
            }
        }

        // Success return.
        return 0;
    }

    /**
     * Executes a command in a separate process, and discards any stdout
     * or stderr output generated by it.
     * Simply calling <code>Runtime.exec</code> can block the process
     * until its output is consumed.
     *
     * @param  cmdarray  array containing the command to call and its args
     */
    private static void execBackground( String[] cmdarray ) throws IOException {
        Process process = Runtime.getRuntime().exec( cmdarray );
        discardBytes( process.getInputStream() );
        discardBytes( process.getErrorStream() );
    }

    /**
     * Ensures that any bytes from a given input stream are discarded.
     *
     * @param  in  input stream
     */
    private static void discardBytes( final InputStream in ) {
        Thread eater = new Thread( "StreamEater" ) {
            public void run() {
                try {
                    while ( in.read() >= 0 ) {}
                    in.close();
                }
                catch ( IOException e ) {
                }
            }
        };
        eater.setDaemon( true );
        eater.start();
    }
}
