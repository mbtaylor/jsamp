package org.astrogrid.samp.xmlrpc;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import org.astrogrid.samp.gui.GuiHubService;
import org.astrogrid.samp.gui.MessageTrackerHubService;
import org.astrogrid.samp.hub.HubService;
import org.astrogrid.samp.hub.BasicHubService;

/**
 * Specifies a particular hub implementation for use with {@link HubRunner}.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
public abstract class HubMode {
    // This class looks like an enumeration-type class to external users.
    // It is actually a HubService factory.

    private final String name_;
    private final boolean isDaemon_;

    /** Hub mode with no GUI representation of hub operations. */
    public static final HubMode NO_GUI;

    /** Hub mode with a GUI representation of connected clients. */
    public static final HubMode CLIENT_GUI;

    /** Hub mode with a GUI representation of clients and messages. */
    public static HubMode MESSAGE_GUI;

    /** Array of available hub modes. */
    private static final HubMode[] KNOWN_MODES = new HubMode[] {
        NO_GUI = createBasicHubMode( "no-gui" ),
        CLIENT_GUI = createGuiHubMode( "client-gui" ),
        MESSAGE_GUI = createMessageTrackerHubMode( "msg-gui" ),
    };

    /**
     * Constructor.
     *
     * @param   name  mode name
     * @param   isDaemon  true if the hub will start only daemon threads
     */
    HubMode( String name, boolean isDaemon ) {
        name_ = name;
        isDaemon_ = isDaemon;
    }

    /**
     * Returns a new HubService object.
     *
     * @param  random  random number generator
     * @param  runners  1-element array of HubRunners - this should be
     *         populated with the runner once it has been constructed
     */
    abstract HubService createHubService( Random random, HubRunner[] runners );

    /**
     * Indicates whether the hub service will start only daemon threads.
     * If it returns true, the caller may need to make sure that the
     * JVM doesn't stop too early.
     *
     * @return   true iff no non-daemon threads will be started by the service
     */
    boolean isDaemon() {
        return isDaemon_;
    }

    /**
     * Returns this mode's name.
     *
     * @return  mode name
     */
    String getName() {
        return name_;
    }

    public String toString() {
        return name_;
    }

    /**
     * Returns one of the known modes which has a name as given.
     *
     * @param  name  mode name (case-insensitive)
     * @return  mode with given name, or null if none known
     */
    public static HubMode getModeFromName( String name ) {
        HubMode[] modes = KNOWN_MODES;
        for ( int im = 0; im < modes.length; im++ ) {
            HubMode mode = modes[ im ];
            if ( mode.name_.equalsIgnoreCase( name ) ) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Returns an array of the hub modes which can actually be used.
     *
     * @return  available mode list
     */
    public static HubMode[] getAvailableModes() {
        List modeList = new ArrayList();
        for ( int i = 0; i < KNOWN_MODES.length; i++ ) {
            HubMode mode = KNOWN_MODES[ i ];
            if ( ! ( mode instanceof BrokenHubMode ) ) {
                modeList.add( mode );
            }
        }
        return (HubMode[]) modeList.toArray( new HubMode[ 0 ] );
    }

    /**
     * Used to perform common configuration of hub display windows 
     * for GUI-type hub modes.
     *
     * @param  frame  hub window
     * @param  runners  1-element array which will contain an associated
     *         hub runner object if one exists
     */
    static void configureHubWindow( JFrame frame,
                                    final HubRunner[] runners ) {
        frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        frame.addWindowListener( new WindowAdapter() {
            public void windowClosed( WindowEvent evt ) {
                HubRunner runner = runners[ 0 ];
                if ( runner != null ) {
                    runner.shutdown();
                }
            }
        } );
        frame.setVisible( true );
    }

    /**
     * Constructs a mode for BasicHubService.
     *
     * @return  non-gui mode
     */
    private static HubMode createBasicHubMode( String name ) {
        try {
            return new HubMode( name, true ) {
                HubService createHubService( Random random,
                                             HubRunner[] runners ) {
                    return new BasicHubService( random );
                }
            };
        }
        catch ( Throwable e ) {
            return new BrokenHubMode( name, e );
        }
    }

    /**
     * Constructs a mode for GuiHubService.
     *
     * @return  mode without message tracking
     */
    private static HubMode createGuiHubMode( String name ) {
        try {
            GuiHubService.class.getName();
            return new HubMode( name, false ) {
                HubService createHubService( Random random,
                                             final HubRunner[] runners ) {
                    return new GuiHubService( random ) {
                        public void start() {
                            super.start();
                            configureHubWindow( createHubWindow(), runners );
                        }
                    };
                }
            };
        }
        catch ( Throwable e ) {
            return new BrokenHubMode( name, e );
        }
    }

    /**
     * Constructs a mode for MessageTrackerHubService.
     *
     * @return   mode with message tracking
     */
    private static HubMode createMessageTrackerHubMode( String name ) {
        try {
            MessageTrackerHubService.class.getName();
            return new HubMode( name, false ) {
                HubService createHubService( Random random,
                                             final HubRunner[] runners ) {
                    return new MessageTrackerHubService( random ) {
                        public void start() {
                            super.start();
                            configureHubWindow( createHubWindow(), runners );
                        }
                    };
                }
            };
        }
        catch ( Throwable e ) {
            return new BrokenHubMode( name, e );
        }
    }

    /**
     * HubMode implemenetation for modes which cannot be used because they
     * rely on classes unavailable at runtime.
     */
    private static class BrokenHubMode extends HubMode {
        private final Throwable error_;

        /**
         * Constructor.
         *
         * @param   name   mode name
         * @param   error  error explaining why mode is unavailable for use
         */
        BrokenHubMode( String name, Throwable error ) {
            super( name, false );
            error_ = error;
        }
        HubService createHubService( Random random, HubRunner[] runners ) {
            throw new RuntimeException( "Hub mode " + getName()
                                      + " unavailable", error_ );
        }
    }
}
