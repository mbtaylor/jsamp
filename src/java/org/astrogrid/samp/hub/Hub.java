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
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.UtilServer;
import org.astrogrid.samp.web.WebHubProfile;
import org.astrogrid.samp.web.WebHubProfileFactory;
import org.astrogrid.samp.xmlrpc.StandardHubProfile;
import org.astrogrid.samp.xmlrpc.StandardHubProfileFactory;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Class which manages a hub and its associated profiles.
 * Static methods are provided for starting a hub in the current or an
 * external JVM, and a <code>main()</code> method is provided for
 * use from the command line.
 *
 * <p>Some of the static methods allow you to indicate which hub profiles
 * should be used, others use a default.  The default list can be set
 * programmatically by using the {@link #setDefaultProfileClasses} method
 * or externally by using the 
 * {@value #HUBPROFILES_PROP} and {@value #EXTRAHUBPROFILES_PROP}
 * system properties.
 * So, for instance, running an application with
 * <code>-Djsamp.hub.profiles=web,std</code> will cause it to run hubs
 * using both the Standard and Web profiles if it does not explicitly choose
 * profiles.
 *
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public class Hub {

    private final HubService service_;
    private final List profileList_;
    private static Class[] defaultDefaultProfileClasses_ = {
        StandardHubProfile.class,
        WebHubProfile.class,
    };
    private static Class[] defaultDefaultExtraProfileClasses_ = {
    };
    private static Class[] defaultProfileClasses_ =
        createDefaultProfileClasses( false );
    private static Class[] defaultExtraProfileClasses_ =
        createDefaultProfileClasses( true );
    private static final Logger logger_ =
        Logger.getLogger( Hub.class.getName() );

    /**
     * System property name for supplying default profiles ({@value})
     * available at hub startup.
     * The value of this property, if any, will be fed to 
     * {@link #parseProfileList}.
     */
    public static final String HUBPROFILES_PROP =
        "jsamp.hub.profiles";

    /**
     * System property name for supplying default profiles ({@value})
     * additional to those in {@link #HUBPROFILES_PROP} which will be
     * supported by the hub but switched off at hub startup time.
     * The value of this property, if any, will be fed to 
     * {@link #parseProfileList}.
     */
    public static final String EXTRAHUBPROFILES_PROP =
        "jsamp.hub.profiles.extra";

    /**
     * Constructor.
     * Note that this object does not start the service, it must be
     * started explicitly, either before or after this constructor is called.
     *
     * @param   service   hub service
     */
    public Hub( HubService service ) {
        service_ = service;
        profileList_ = new ArrayList();
    }

    /**
     * Stops this hub and its profiles running.
     */
    public synchronized void shutdown() {
        logger_.info( "Shutting down hub service" );
        service_.shutdown();
        for ( Iterator it = profileList_.iterator(); it.hasNext(); ) {
            HubProfile profile = (HubProfile) it.next();
            logger_.info( "Shutting down hub profile "
                        + profile.getProfileName() );
            try {
                profile.stop();
            }
            catch ( IOException e ) {
                logger_.log( Level.WARNING,
                             "Failed to stop profile "
                           + profile.getProfileName(), e );
            }
            it.remove();
        }
    }

    /**
     * Starts a profile running on behalf of this hub.
     *
     * @param  profile to start
     */
    public synchronized void startProfile( final HubProfile profile )
            throws IOException {
        if ( profileList_.contains( profile ) ) {
            logger_.info( "Profile " + profile.getProfileName()
                        + " already started in this hub" );
        }
        else {
            profile.start( new ClientProfile() {
                public HubConnection register() throws SampException {
                    return service_.register( profile );
                }
                public boolean isHubRunning() {
                    return service_.isHubRunning();
                }
            } );
            profileList_.add( profile );
        }
    }

    /**
     * Stops a profile running on behalf of this hub, and disconnects
     * all clients registered with it.
     *
     * @param  profile  profile to stop
     */
    public synchronized void stopProfile( HubProfile profile ) {
        logger_.info( "Shutting down hub profile " + profile.getProfileName()
                    + " and disconnecting clients" );
        try {
            profile.stop();
        }
        catch ( IOException e ) {
            logger_.log( Level.WARNING,
                         "Failed to stop profile "
                       + profile.getProfileName(), e );
        }
        profileList_.remove( profile );
        service_.disconnectAll( profile );
    }

    /**
     * Returns a standard list of known HubProfileFactories.
     * This is used when parsing hub profile lists
     * ({@link #parseProfileList} to supply the well-known named profiles.
     *
     * @return   array of known hub profile factories
     */
    public static HubProfileFactory[] getKnownHubProfileFactories() {
        return new HubProfileFactory[] {
            new StandardHubProfileFactory(),
            new WebHubProfileFactory(),
        };
    }

    /**
     * Returns a copy of the default set of HubProfile classes used
     * when a hub is run and the list of profiles is not set explicitly.
     * Each element should be an implementation of {@link HubProfile}
     * with a no-arg constructor.
     *
     * @param   extra  false for starting classes, true for additional ones
     * @return   array of hub profile classes
     */
    public static Class[] getDefaultProfileClasses( boolean extra ) {
        return (Class[]) ( extra ? defaultExtraProfileClasses_
                                 : defaultProfileClasses_ ).clone();
    }

    /**
     * Sets the default set of HubProfile classes.
     *
     * @param  clazzes  array to be returned by getDefaultProfileClasses
     * @param   extra  false for starting classes, true for additional ones
     */
    public static void setDefaultProfileClasses( Class[] clazzes,
                                                 boolean extra ) {
        for ( int ip = 0; ip < clazzes.length; ip++ ) {
            Class clazz = clazzes[ ip ];
            if ( ! HubProfile.class.isAssignableFrom( clazz ) ) {
                throw new IllegalArgumentException( "Class " + clazz.getName()
                                                  + " not a HubProfile" );
            }
        }
        clazzes = (Class[]) clazzes.clone();
        if ( extra ) {
            defaultExtraProfileClasses_ = clazzes;
        }
        else {
            defaultProfileClasses_ = clazzes;
        }
    }

    /**
     * Invoked at class load time to come up with the list of hub 
     * profiles to use when no profiles are specified explicitly.
     * By default this is just the standard profile, but if the
     * {@link #HUBPROFILES_PROP} system property is defined its value
     * is used instead.
     *
     * @param   extra  false for starting classes, true for additional ones
     * @return  default array of hub profile classes
     */
    private static Class[] createDefaultProfileClasses( boolean extra ) {
        String listTxt = System.getProperty( extra ? EXTRAHUBPROFILES_PROP
                                                        : HUBPROFILES_PROP );
        if ( listTxt != null ) {
            HubProfileFactory[] facts = parseProfileList( listTxt );
            Class[] clazzes = new Class[ facts.length ];
            for ( int i = 0; i < facts.length; i++ ) {
                clazzes[ i ] = facts[ i ].getHubProfileClass();
            }
            return clazzes;
        }
        else {
            return extra ? defaultDefaultExtraProfileClasses_
                         : defaultDefaultProfileClasses_;
        }
    }

    /**
     * Parses a string representing a list of hub profiles.
     * The result is an array of HubProfileFactories.
     * The list is comma-separated, and each element may be
     * <em>either</em> the {@link HubProfileFactory#getName name}
     * of a HubProfileFactory
     * <em>or</em> the classname of a {@link HubProfile} implementation
     * with a suitable no-arg constructor.
     *
     * @param  listTxt  comma-separated list
     * @return  array of hub profile factories
     * @throws   IllegalArgumentException  if unknown
     */
    public static HubProfileFactory[] parseProfileList( String listTxt ) {
        String[] txtItems = listTxt == null || listTxt.trim().length() == 0
                          ? new String[ 0 ]
                          : listTxt.split( "," );
        List factoryList = new ArrayList();
        for ( int i = 0; i < txtItems.length; i++ ) {
            factoryList.add( parseProfileClass( txtItems[ i ] ) );
        }
        return (HubProfileFactory[])
               factoryList.toArray( new HubProfileFactory[ 0 ] );
    }

    /**
     * Parses a string representing a hub profile.  Each element may be
     * <em>either</em> the {@link HubProfileFactory#getName name}
     * of a HubProfileFactory
     * <em>or</em> the classname of a {@link HubProfile} implementation
     * with a suitable no-arg constructor.
     *
     * @param  txt  string
     * @return  hub profile factory
     * @throws   IllegalArgumentException  if unknown
     */
    private static HubProfileFactory parseProfileClass( String txt ) {
        HubProfileFactory[] profFacts = getKnownHubProfileFactories();
        for ( int i = 0; i < profFacts.length; i++ ) {
            if ( txt.equals( profFacts[ i ].getName() ) ) {
                return profFacts[ i ];
            }
        }
        final Class clazz;
        try {
            clazz = Class.forName( txt );
        }
        catch ( ClassNotFoundException e ) {
            throw (IllegalArgumentException)
                  new IllegalArgumentException( "No known hub/class " + txt )
                 .initCause( e );
        }
        if ( HubProfile.class.isAssignableFrom( clazz ) ) {
            return new HubProfileFactory() {
                public Class getHubProfileClass() {
                    return clazz;
                }
                public String[] getFlagsUsage() {
                    return new String[ 0 ];
                }
                public String getName() {
                    return clazz.getName();
                }
                public HubProfile createHubProfile( List flagList )
                        throws IOException {
                    try {
                        return (HubProfile) clazz.newInstance();
                    }
                    catch ( IllegalAccessException e ) {
                        throw (IOException)
                              new IOException( "Can't create " + clazz.getName()
                                             + " instance" )
                             .initCause( e );
                    }
                    catch ( InstantiationException e ) {
                        throw (IOException)
                              new IOException( "Can't create " + clazz.getName()
                                             + " instance" )
                             .initCause( e );
                    }
                    catch ( ExceptionInInitializerError e ) {
                        Throwable cause = e.getCause();
                        if ( cause instanceof IOException ) {
                            throw (IOException) cause;
                        }
                        else {
                            throw (IOException)
                                  new IOException( "Can't create "
                                                 + clazz.getName()
                                                 + " instance" )
                                 .initCause( e );
                        }
                    }
                }
            };
        }
        else {
            throw new IllegalArgumentException( clazz + " is not a "
                                              + HubProfile.class.getName() );
        }
    }

    /**
     * Returns an array of default Hub Profiles.
     * This is the result of calling the no-arg constructor
     * for each element of the result of {@link #getDefaultProfileClasses}.
     *
     * @param   extra  false for starting profiles, true for additional ones
     * @return   array of hub profiles to use by default
     */
    public static HubProfile[] createDefaultProfiles( boolean extra ) {
        Class[] clazzes = getDefaultProfileClasses( extra );
        List hubProfileList = new ArrayList();
        for ( int ip = 0; ip < clazzes.length; ip++ ) {
            Class clazz = clazzes[ ip ];
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
            catch ( ExceptionInInitializerError e ) {
                logger_.warning( "No hub profile " + clazz.getName()
                               + " - construction error"
                               + " (" + e.getCause() + ")" );
            }
        }
        return (HubProfile[]) hubProfileList.toArray( new HubProfile[ 0 ] );
    }

    /**
     * Starts a SAMP hub with given sets of profiles.
     * The returned hub is running.
     *
     * <p>The <code>profiles</code> argument gives the profiles which will
     * be started initially, and the <code>extraProfiles</code> argument
     * lists more that can be started under user control later.
     * If either or both list is given as null, suitable defaults will be used.
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
     * @param   profiles   SAMP profiles to support on hub startup;
     *                     if null a default set will be used
     * @param   extraProfiles  SAMP profiles to offer for later startup under
     *                     user control; if null a default set will be used
     * @return  running hub
     */
    public static Hub runHub( HubServiceMode hubMode, HubProfile[] profiles,
                              HubProfile[] extraProfiles )
            throws IOException {

        // Get values for the list of starting profiles, and the list of
        // additional profiles that may be started later.
        // If these have not been specified explicitly, use defaults.
        if ( profiles == null ) {
            profiles = createDefaultProfiles( false );
        }
        if ( extraProfiles == null ) {
            extraProfiles = createDefaultProfiles( true );
        }
        List profList = new ArrayList();
        profList.addAll( Arrays.asList( profiles ) );
        for ( int ip = 0; ip < extraProfiles.length; ip++ ) {
            HubProfile ep = extraProfiles[ ip ];
            boolean gotit = false;
            for ( int jp = 0; jp < profiles.length; jp++ ) {
                gotit = gotit
                     || profiles[ jp ].getClass().equals( ep.getClass() );
            }
            if ( ! gotit ) {
                profList.add( ep );
            }
        }
        HubProfile[] allProfiles =
            (HubProfile[]) profList.toArray( new HubProfile[ 0 ] );

        // Construct a hub service ready to use the full profile list.
        final Hub[] runners = new Hub[ 1 ];
        HubService hubService =
            hubMode.createHubService( KeyGenerator.createRandom(),
                                      allProfiles, runners );
        final Hub hub = new Hub( hubService );
        runners[ 0 ] = hub;

        // Start the starting profiles.
        int nStarted = 0;
        try {
            for ( int ip = 0; ip < profiles.length; ip++ ) {
                logger_.info( "Starting hub profile "
                            + profiles[ ip ].getProfileName() );
                hub.startProfile( profiles[ ip ] );
                nStarted++;
            }
        }
        catch ( IOException e ) {
            for ( int ip = 0; ip < nStarted; ip++ ) {
                logger_.info( "Stopping hub profile "
                            + profiles[ ip ].getProfileName()
                            + " because of startup error" );
                profiles[ ip ].stop();
            }
            throw e;
        }

        // Start the hub service itself.
        logger_.info( "Starting hub service" );
        hubService.start();
        Runtime.getRuntime().addShutdownHook( new Thread( "Hub Terminator" ) {
            public void run() {
                hub.shutdown();
            }
        } );

        // Return the running hub.
        return hub;
    }

    /**
     * Starts a SAMP hub with a default set of profiles.
     * This convenience method invokes <code>runHub(hubMode,null,null)</code>.
     *
     * @param   hubMode  hub mode
     * @return  running hub
     * @see    #runHub(HubServiceMode,HubProfile[],HubProfile[])
     */
    public static Hub runHub( HubServiceMode hubMode ) throws IOException {
        return runHub( hubMode, null, null );
    }

    /**
     * Attempts to start a hub in a new JVM with a given set
     * of profiles.  The resulting hub can therefore outlast the
     * lifetime of the current application.
     * Because of the OS interaction required, it's hard to make this
     * bulletproof, and it may fail without an exception, but we do our best.
     *
     * <p>The classes specified by the <code>profileClasses</code> and
     * <code>extraProfileClasses</code> arguments must implement
     * {@link HubProfile} and must have a no-arg constructor.
     * If null is given in either case suitable defaults, taken from the
     * current JVM, are used.
     *
     * @param   hubMode  hub mode
     * @param   profileClasses  hub profile classes to start on hub startup
     * @param   extraProfileClasses  hub profile classes which may be started
     *          later under user control
     * @see     #checkExternalHubAvailability
     */
    public static void runExternalHub( HubServiceMode hubMode,
                                       Class[] profileClasses,
                                       Class[] extraProfileClasses )
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
            HUBPROFILES_PROP,
            EXTRAHUBPROFILES_PROP,
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
            argList.add( "-profiles" );
            StringBuffer profArg = new StringBuffer();
            for ( int ip = 0; ip < profileClasses.length; ip++ ) {
                if ( ip > 0 ) {
                    profArg.append( ',' );
                }
                profArg.append( profileClasses[ ip ].getName() );
            }
            argList.add( profArg.toString() );
        }
        if ( extraProfileClasses != null ) {
            argList.add( "-extraprofiles" );
            StringBuffer eprofArg = new StringBuffer();
            for ( int ip = 0; ip < profileClasses.length; ip++ ) {
                if ( ip > 0 ) {
                    eprofArg.append( ',' );
                }
                eprofArg.append( extraProfileClasses[ ip ].getName() );
            }
            argList.add( eprofArg.toString() );
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
     * <code>runExternalHub(hubMode,null,null)</code>.
     *
     * @param   hubMode  hub mode
     * @see #runExternalHub(HubServiceMode,java.lang.Class[],java.lang.Class[])
     */
    public static void runExternalHub( HubServiceMode hubMode )
            throws IOException {
        runExternalHub( hubMode, null, null );
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
        StringBuffer pbuf = new StringBuffer();
        for ( int ip = 0; ip < knownProfileFactories.length; ip++ ) {
            pbuf.append( knownProfileFactories[ ip ].getName() )
                .append( '|' );
        }
        pbuf.append( "<hubprofile-class>" )
            .append( "[,...]" );
        String profUsage = pbuf.toString();
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
            .append( " [" )
            .append( "-profiles " )
            .append( profUsage )
            .append( "]" )
            .append( "\n           " )
            .append( " [" )
            .append( "-extraprofiles " )
            .append( profUsage )
            .append( "]" );
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
        String profilesTxt = null;
        String extraProfilesTxt = null;
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
            else if ( arg.equals( "-profiles" ) ) {
                it.remove();
                if ( it.hasNext() ) {
                    profilesTxt = (String) it.next();
                    it.remove();
                }
                else {
                    System.err.println( usage );
                    return 1;
                }
            }
            else if ( arg.equals( "-extraprofiles" ) ) {
                it.remove();
                if ( it.hasNext() ) {
                    extraProfilesTxt = (String) it.next();
                    it.remove();
                }
                else {
                    System.err.println( usage );
                    return 1;
                }
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

        // Assemble list of profiles to use.
        final HubProfile[] profiles;
        if ( profilesTxt == null ) {
            profiles = createDefaultProfiles( false );
        }
        else {
            HubProfileFactory[] pfacts;
            try {
                pfacts = parseProfileList( profilesTxt );
            }
            catch ( IllegalArgumentException e ) {
                System.err.println( e.getMessage() );
                System.err.println( "Usage: -profiles " + profUsage );
                return 1;
            }
            profiles = new HubProfile[ pfacts.length ];
            for ( int i = 0; i < pfacts.length; i++ ) {
                HubProfileFactory pfact = pfacts[ i ];
                try {
                    profiles[ i ] = pfact.createHubProfile( argList );
                }
                catch ( RuntimeException e ) {
                    System.err.println( "Error configuring profile "
                                      + pfact.getName() + ":\n"
                                      + e.getMessage() );
                    return 1;
                }
             
            }
        }
        final HubProfile[] extraProfiles;
        if ( extraProfilesTxt == null ) {
            extraProfiles = createDefaultProfiles( true );
        }
        else {
            HubProfileFactory[] pfacts;
            try {
                pfacts = parseProfileList( extraProfilesTxt );
            }
            catch ( IllegalArgumentException e ) {
                System.err.println( e.getMessage() );
                System.err.println( "Usage: -extraprofiles " + profUsage );
                return 1;
            }
            extraProfiles = new HubProfile[ pfacts.length ];
            for ( int i = 0; i < pfacts.length; i++ ) {
                HubProfileFactory pfact = pfacts[ i ];
                try {
                    extraProfiles[ i ] = pfact.createHubProfile( argList );
                }
                catch ( RuntimeException e ) {
                    System.err.println( "Error configuring profile "
                                      + pfact.getName() + ":\n"
                                      + e.getMessage() );
                    return 1;
                }
            }
        }

        // Check all command line args have been used.
        if ( ! argList.isEmpty() ) {
            System.err.println( "Some args not used " + argList );
            System.err.println( usage );
            return 1;
        }

        // Start hub service and install profile-specific interfaces.
        runHub( hubMode, profiles, extraProfiles );

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
