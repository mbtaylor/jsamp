package org.astrogrid.samp.gui;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.event.ActionListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Provides basic access to the windowing system's System Tray.
 * This is a facade for a subset of the Java 1.6 java.awt.SystemTray
 * functionality.  When running in a J2SE1.6 JRE it will use reflection
 * to access the underlying classes.  In an earlier JRE, it will report
 * lack of support.
 *
 * @author   Mark Taylor
 * @since    20 Jul 2010
 */
public abstract class SysTray {

    private static SysTray instance_;
    private static final Logger logger_ =
        Logger.getLogger( SysTray.class.getName() );

    /**
     * Indicates whether system tray functionality is available.
     *
     * @return  true iff the addIcon/removeIcon methods are expected to work
     */
    public abstract boolean isSupported();

    /**
     * Adds an icon to the system tray.
     *
     * @param   im  image for display
     * @param  tooltip  tooltip text, or null
     * @param  popup   popup menu, or null
     * @param  iconListener  listener triggered when icon is activated, or null
     * @return  tray icon object, may be used for later removal
     */
    public abstract Object addIcon( Image im, String tooltip, PopupMenu popup,
                                    ActionListener iconListener )
            throws AWTException;

    /**
     * Removes a previously-added icon from the tray.
     *
     * @param  trayIcon  object obtained from a previous invocation of 
     *                   addIcon
     */
    public abstract void removeIcon( Object trayIcon )
            throws AWTException;

    /**
     * Returns an instance of this class.
     *
     * @return  instance
     */
    public static SysTray getInstance() {
        if ( instance_ == null ) {
            String jvers = System.getProperty( "java.specification.version" );
            boolean isJava6 =
                jvers != null && jvers.matches( "^[0-9]+\\.[0-9]+$" )
                              && Double.parseDouble( jvers ) > 1.5999;
            if ( ! isJava6 ) {
                logger_.info( "Not expecting system tray support"
                            + " (java version < 1.6)" );
            }
            SysTray instance;
            try {
                instance = new Java6SysTray();
            }
            catch ( Throwable e ) {
                if ( isJava6 ) {
                    logger_.info( "No system tray support: " + e );
                }
                instance = new NoSysTray();
            }
            instance_ = instance;
        }
        return instance_;
    }

    /**
     * Implementation which provides no system tray access.
     */
    private static class NoSysTray extends SysTray {
        public boolean isSupported() {
            return false;
        }
        public Object addIcon( Image im, String tooltip, PopupMenu popup,
                               ActionListener iconListener ) {
            throw new UnsupportedOperationException();
        }
        public void removeIcon( Object trayIcon ) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Implementation which provides system tray access using J2SE 1.6 classes
     * by reflection.
     */
    private static class Java6SysTray extends SysTray {
        private final Class systemTrayClass_;
        private final Method addMethod_;
        private final Method removeMethod_;
        private final Class trayIconClass_;
        private final Constructor trayIconConstructor_;
        private final Method setImageAutoSizeMethod_;
        private final Method addActionListenerMethod_;
        private final Object systemTrayInstance_;

        /**
         * Constructor.
         */
        Java6SysTray() throws ClassNotFoundException, IllegalAccessException,
                              NoSuchMethodException, InvocationTargetException {
            systemTrayClass_ = Class.forName( "java.awt.SystemTray" );
            trayIconClass_ = Class.forName( "java.awt.TrayIcon" );
            addMethod_ =
                systemTrayClass_
               .getMethod( "add", new Class[] { trayIconClass_ } );
            removeMethod_ =
                systemTrayClass_
               .getMethod( "remove", new Class[] { trayIconClass_ } );
            trayIconConstructor_ =
                trayIconClass_
               .getConstructor( new Class[] { Image.class, String.class,
                                              PopupMenu.class } );
            setImageAutoSizeMethod_ =
                trayIconClass_
               .getMethod( "setImageAutoSize", new Class[] { boolean.class } );
            addActionListenerMethod_ =
                trayIconClass_
               .getMethod( "addActionListener",
                           new Class[] { ActionListener.class } );
            boolean isSupported =
                Boolean.TRUE
               .equals( systemTrayClass_
                       .getMethod( "isSupported", new Class[ 0 ] )
                       .invoke( null, new Object[ 0 ] ) );
            systemTrayInstance_ =
                isSupported ? systemTrayClass_
                             .getMethod( "getSystemTray", new Class[ 0 ] )
                             .invoke( null, new Object[ 0 ] )
                            : null;
        }

        public boolean isSupported() {
            return systemTrayInstance_ != null;
        }

        public Object addIcon( Image im, String tooltip, PopupMenu popup,
                               ActionListener iconListener )
                throws AWTException {
            try {
                Object trayIcon =
                   trayIconConstructor_
                  .newInstance( new Object[] { im, tooltip, popup } );
                setImageAutoSizeMethod_
                    .invoke( trayIcon, new Object[] { Boolean.TRUE } );
                if ( iconListener != null ) {
                    addActionListenerMethod_
                        .invoke( trayIcon, new Object[] { iconListener } );
                }
                addMethod_.invoke( systemTrayInstance_,
                                   new Object[] { trayIcon } );
                return trayIcon;
            }
            catch ( InvocationTargetException e ) {
                String msg = e.getCause() instanceof AWTException
                           ? e.getCause().getMessage()
                           : "Add tray icon invocation failed";
                throw (AWTException) new AWTException( msg ).initCause( e );
            }
            catch ( Exception e ) {
                throw (AWTException)
                      new AWTException( "Add tray icon invocation failed" )
                     .initCause( e );
            }
        }

        public void removeIcon( Object trayIcon ) throws AWTException {
            try {
                removeMethod_.invoke( systemTrayInstance_,
                                      new Object[] { trayIcon } );
            }
            catch ( Exception e ) {
                throw (AWTException)
                      new AWTException( "Remove tray icon invocation failed" )
                     .initCause( e );
            }
        }
    }
}
