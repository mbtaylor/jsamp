package org.astrogrid.samp;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.hub.Hub;
import org.astrogrid.samp.web.WebClientProfile;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Convenience class for invoking JSAMP command-line applications.
 *
 * @author   Mark Taylor
 * @since    23 Jul 2008
 */
public class JSamp {

    /** Known command class names. */
    static final String[] COMMAND_CLASSES = new String[] {
        "org.astrogrid.samp.hub.Hub",
        "org.astrogrid.samp.gui.HubMonitor",
        "org.astrogrid.samp.util.SampLoad",
        "org.astrogrid.samp.test.Snooper",
        "org.astrogrid.samp.test.MessageSender",
        "org.astrogrid.samp.test.HubTester",
        "org.astrogrid.samp.test.CalcStorm",
        "org.astrogrid.samp.bridge.Bridge",
    };

    /**
     * Private sole constructor prevents instantiation.
     */
    private JSamp() {
    }

    /**
     * Does the work for the main method.
     */
    public static int runMain( String[] args ) {

        // Assemble usage message.
        StringBuffer ubuf = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( JSamp.class.getName() )
            .append( " [-help]" )
            .append( " [-version]" )
            .append( " <command>" )
            .append( " [-help]" )
            .append( " <cmd-args>" )
            .append( "\n      " )
            .append( "<command-class>" )
            .append( " [-help]" )
            .append( " <cmd-args>" )
            .append( "\n" )
            .append( "\n   Commands (command-classes) are:" );
        for ( int ic = 0; ic < COMMAND_CLASSES.length; ic++ ) {
            String className = COMMAND_CLASSES[ ic ];
            ubuf.append( "\n      " )
                .append( abbrev( className ) );
            int pad = 14 - abbrev( className ).length();
            for ( int i = 0; i < pad; i++ ) {
                ubuf.append( ' ' );
            }
            ubuf.append( "  (" )
                .append( className )
                .append( ")" );
        }
        ubuf.append( "\n" )
            .append( "\n   " )
            .append( "Environment Variable:" )
            .append( "\n      " )
            .append( DefaultClientProfile.HUBLOC_ENV )
            .append( "           = " )
            .append( StandardClientProfile.STDPROFILE_HUB_PREFIX )
            .append( "<url>" )
            .append( "|" )
            .append( WebClientProfile.WEBPROFILE_HUB_PREFIX )
            .append( "<name>" )
            .append( "\n                                            " )
            .append( "|" )
            .append( DefaultClientProfile.HUBLOC_CLASS_PREFIX )
            .append( "<clientprofile-class>" )
            .append( "\n" )
            .append( "\n   " )
            .append( "System Properties:" )
            .append( "\n      " )
            .append( "jsamp.hub.profiles   = " )
            .append( "std|web|<hubprofile-class>[,...]" )
            .append( "\n      " )
            .append( "jsamp.localhost      = " )
            .append( "\"[hostname]\"|\"[hostnumber]\"|<value>" )
            .append( "\n      " )
            .append( "jsamp.server.port    = " )
            .append( "<port-number>" )
            .append( "\n      " )
            .append( "jsamp.web.extrahosts = " )
            .append( "<host>[,...]" )
            .append( "\n      " )
            .append( "jsamp.xmlrpc.impl    = " )
            .append( formatImpls( XmlRpcKit.KNOWN_IMPLS, XmlRpcKit.class ) )
            .append( "\n" );
        String usage = ubuf.toString();
 
        // Perform general tweaks.
        setDefaultProperty( "java.awt.Window.locationByPlatform", "true" );

        // Process command line arguments.
        List argList = new ArrayList( Arrays.asList( args ) );
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.toLowerCase().equals( "hubrunner" ) ) {
                System.err.println( "\"hubrunner\" command is deprecated. "
                                  + "Use \"hub\" instead." );
                return 1;
            }
            for ( int ic = 0; ic < COMMAND_CLASSES.length; ic++ ) {
                String className = COMMAND_CLASSES[ ic ];
                if ( arg.toLowerCase()
                        .equals( abbrev( className ).toLowerCase() ) ) {
                    it.remove();
                    return runCommand( className,
                                       (String[])
                                       argList.toArray( new String[ 0 ] ) );
                }
            }
            if ( arg.startsWith( "-h" ) ) {
                System.out.println( usage );
                return 0;
            }
            else if ( arg.startsWith( "-vers" ) ) {
                System.out.println();
                System.out.println( getVersionText() );
                System.out.println();
                return 0;
            }
            else {
                System.err.println( usage );
                return 1;
            }
        }

        // No arguments.
        assert argList.isEmpty();
        System.err.println( JSamp.class.getName() + " invoked with no arguments"
                        + " - running hub" );
        System.err.println( "Use \"-help\" flag for more options" );
        System.err.println( "Use \"hub\" argument"
                          + " to suppress this message" );
        return runCommand( Hub.class.getName(), new String[ 0 ] );
    }

    /**
     * Runs a command.
     *
     * @param  className  name of a class with a <code>main(String[])</code>
     *                    method
     * @param  args  arguments as if passed from the command line
     */
    private static int runCommand( String className, String[] args ) {
        Class clazz;
        try {
            clazz = Class.forName( className );
        }
        catch ( ClassNotFoundException e ) {
            System.err.println( "Class " + className + " not available" );
            return 1;
        }
        Object statusObj;
        try {
            getMainMethod( clazz ).invoke( null, new Object[] { args } );
            return 0;
        }
        catch ( InvocationTargetException e ) {
            e.getCause().printStackTrace();
            return 1;
        }
        catch ( Exception e ) {
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Returns the <code>main(String[])</code> method for a given class.
     */
    static Method getMainMethod( Class clazz ) {
        Method method;
        try {
            method = clazz.getMethod( "main",
                                      new Class[] { String[].class } );
        }
        catch ( NoSuchMethodException e ) {
            throw (IllegalArgumentException)
                  new AssertionError( "main() method missing for " 
                                    + clazz.getName() )
                 .initCause( e );
        }
        int mods = method.getModifiers();
        if ( Modifier.isStatic( mods ) &&
             Modifier.isPublic( mods ) &&
             method.getReturnType() == void.class ) {
            return method;
        }
        else {
            throw new IllegalArgumentException( "Wrong main() method signature"
                                              + " for " + clazz.getName() );
        }
    }

    /**
     * Returns the abbreviated form of a given class name.
     *
     * @param  className  class name
     * @return  abbreviation
     */
    private static String abbrev( String className ) {
        return className.substring( className.lastIndexOf( "." ) + 1 )
                        .toLowerCase();
    }

    private static String formatImpls( Object[] options, Class clazz ) {
        StringBuffer sbuf = new StringBuffer();
        if ( options != null ) {
            for ( int i = 0; i < options.length; i++ ) {
                if ( sbuf.length() > 0 ) {
                    sbuf.append( '|' );
                }
                sbuf.append( options[ i ] );
            }
        }
        if ( clazz != null ) {
            if ( sbuf.length() > 0 ) {
                sbuf.append( '|' );
            }
            sbuf.append( '<' )
                .append( clazz.getName().replaceFirst( "^.*\\.", "" )
                                        .toLowerCase() )
                .append( "-class" )
                .append( '>' );
        }
        return sbuf.toString();
    }

    /**
     * Returns a string giving version details for this package.
     *
     * @return  version string
     */
    private static String getVersionText() {
        return new StringBuffer()
           .append( "   This is JSAMP.\n" )
           .append( "\n   " )
           .append( "JSAMP toolkit version:" )
           .append( "\n      " )
           .append( SampUtils.getSoftwareVersion() )
           .append( "\n   " )
           .append( "SAMP standard version:" )
           .append( "\n      " )
           .append( SampUtils.getSampVersion() )
           .append( "\n   " )
           .append( "Author:" )
           .append( "\n      " )
           .append( "Mark Taylor (m.b.taylor@bristol.ac.uk)" )
           .append( "\n   " )
           .append( "WWW:" )
           .append( "\n      " )
           .append( "http://www.star.bristol.ac.uk/mbt/jsamp/" )
           .toString();
    }

    /**
     * Sets a system property to a given value unless it has already been set.
     * If it has a prior value, that is undisturbed.
     * Potential security exceptions are caught and dealt with.
     * 
     * @param   key  property name
     * @param   value  suggested property value
     */
    private static void setDefaultProperty( String key, String value ) {
        String existingVal = System.getProperty( key );
        if ( existingVal == null || existingVal.trim().length() == 0 ) {
            try {
                System.setProperty( key, value );
            }
            catch ( SecurityException e ) {
                // never mind.
            }
        }
    }

    /**
     * Main method.
     * Use -help flag for documentation.
     */
    public static void main( String[] args ) {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }
}
