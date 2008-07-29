package org.astrogrid.samp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Convenience class for invoking JSAMP command-line applications.
 *
 * @author   Mark Taylor
 * @since    23 Jul 2008
 */
public class Samp {

    /** Known command class names. */
    static final String[] COMMAND_CLASSES = new String[] {
        "org.astrogrid.samp.hub.HubRunner",
        "org.astrogrid.samp.gui.HubMonitor",
        "org.astrogrid.samp.test.HubTester",
        "org.astrogrid.samp.test.CalcStorm",
        "org.astrogrid.samp.test.MessageSender",
    };

    /**
     * Private sole constructor prevents instantiation.
     */
    private Samp() {
    }

    /**
     * Does the work for the main method.
     */
    public static int runMain( String[] args ) {

        // Assemble usage message.
        StringBuffer ubuf = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( Samp.class.getName() )
            .append( " [-help]" )
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
        ubuf.append( "\n" );
        String usage = ubuf.toString();

        // Process command line arguments.
        List argList = new ArrayList( Arrays.asList( args ) );
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
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
            else {
                System.err.println( usage );
                return 1;
            }
        }
        assert argList.isEmpty();
        System.err.println( usage );
        return 1;
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
