package org.astrogrid.samp.hub;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.gui.ErrorDialog;
import org.astrogrid.samp.gui.GuiHubService;
import org.astrogrid.samp.gui.MessageTrackerHubService;
import org.astrogrid.samp.gui.SysTray;

/**
 * Specifies a particular hub implementation for use with {@link Hub}.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
public abstract class HubServiceMode {
    // This class looks like an enumeration-type class to external users.
    // It is actually a HubService factory.

    private final String name_;
    private final boolean isDaemon_;
    private static final Logger logger_ =
        Logger.getLogger( HubServiceMode.class.getName() );

    /** Hub mode with no GUI representation of hub operations. */
    public static final HubServiceMode NO_GUI;

    /** Hub mode with a GUI representation of connected clients. */
    public static final HubServiceMode CLIENT_GUI;

    /** Hub mode with a GUI representation of clients and messages. */
    public static HubServiceMode MESSAGE_GUI;

    /** Hub Mode which piggy-backs on an existing hub using
     *  the default client profile. */
    public static HubServiceMode FACADE;

    /** Array of available hub modes. */
    private static final HubServiceMode[] KNOWN_MODES = new HubServiceMode[] {
        NO_GUI = createBasicHubMode( "no-gui" ),
        CLIENT_GUI = createGuiHubMode( "client-gui" ),
        MESSAGE_GUI = createMessageTrackerHubMode( "msg-gui" ),
        FACADE = createFacadeHubMode( "facade" ),
    };

    /**
     * Constructor.
     *
     * @param   name  mode name
     * @param   isDaemon  true if the hub will start only daemon threads
     */
    HubServiceMode( String name, boolean isDaemon ) {
        name_ = name;
        isDaemon_ = isDaemon;
    }

    /**
     * Returns a new HubService object.
     *
     * @param  random  random number generator
     * @param  profiles  hub profiles
     * @param  runners  1-element array of Hubs - this should be
     *         populated with the runner once it has been constructed
     */
    abstract HubService createHubService( Random random, HubProfile[] profiles,
                                          Hub[] runners );

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
    public static HubServiceMode getModeFromName( String name ) {
        HubServiceMode[] modes = KNOWN_MODES;
        for ( int im = 0; im < modes.length; im++ ) {
            HubServiceMode mode = modes[ im ];
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
    public static HubServiceMode[] getAvailableModes() {
        List modeList = new ArrayList();
        for ( int i = 0; i < KNOWN_MODES.length; i++ ) {
            HubServiceMode mode = KNOWN_MODES[ i ];
            if ( ! ( mode instanceof BrokenHubMode ) ) {
                modeList.add( mode );
            }
        }
        return (HubServiceMode[]) modeList.toArray( new HubServiceMode[ 0 ] );
    }

    /**
     * Used to perform common configuration of hub display windows 
     * for GUI-type hub modes.
     *
     * @param  frame  hub window
     * @param  profiles  profiles to run for hub
     * @param  runners  1-element array which will contain an associated
     *         hub runner object if one exists
     * @return  object which should be shutdown when the hub stops running
     */
    private static Tidier configureHubWindow( JFrame frame,
                                              HubProfile[] profiles,
                                              Hub[] runners ) {
        SysTray sysTray = SysTray.getInstance();
        if ( sysTray.isSupported() ) {
            try {
                SysTrayWindowConfig winConfig =
                    new SysTrayWindowConfig( frame, profiles, runners,
                                             sysTray );
                winConfig.configureWindow();
                winConfig.configureSysTray();
                logger_.info( "Hub started in system tray" );
                return winConfig;
            }
            catch ( AWTException e ) {
                logger_.warning( "Failed to install in system tray: " + e );
                BasicWindowConfig winConfig =
                    new BasicWindowConfig( frame, profiles, runners );
                winConfig.configureWindow();
                return winConfig;
            }
        }
        else {
            logger_.info( "System tray not supported: displaying hub window" );
            BasicWindowConfig winConfig =
                new BasicWindowConfig( frame, profiles, runners );
            winConfig.configureWindow();
            return winConfig;
        }
    }

    /**
     * Constructs a mode for BasicHubService.
     *
     * @param   name  mode name
     * @return  non-gui mode
     */
    private static HubServiceMode createBasicHubMode( String name ) {
        try {
            return new HubServiceMode( name, true ) {
                HubService createHubService( Random random,
                                             HubProfile[] profiles,
                                             Hub[] runners ) {
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
    private static HubServiceMode createGuiHubMode( String name ) {
        try {
            GuiHubService.class.getName();
            return new HubServiceMode( name, false ) {
                HubService createHubService( Random random,
                                             final HubProfile[] profiles,
                                             final Hub[] runners ) {
                    return new GuiHubService( random ) {
                        Tidier tidier;
                        public void start() {
                            super.start();
                            SwingUtilities.invokeLater( new Runnable() {
                                public void run() {
                                    tidier =
                                        configureHubWindow( createHubWindow(),
                                                            profiles, runners );
                                }
                            } );
                        }
                        public void shutdown() {
                            super.shutdown();
                            SwingUtilities.invokeLater( new Runnable() {
                                public void run() {
                                    if ( tidier != null ) {
                                        tidier.tidyGui();
                                    }
                                }
                            } );
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
    private static HubServiceMode createMessageTrackerHubMode( String name ) {
        try {
            MessageTrackerHubService.class.getName();
            return new HubServiceMode( name, false ) {
                HubService createHubService( Random random,
                                             final HubProfile[] profiles,
                                             final Hub[] runners ) {
                    return new MessageTrackerHubService( random ) {
                        Tidier tidier;
                        public void start() {
                            super.start();
                            SwingUtilities.invokeLater( new Runnable() {
                                public void run() {
                                    tidier =
                                        configureHubWindow( createHubWindow(),
                                                            profiles,
                                                            runners );
                                }
                            } );
                        }
                        public void shutdown() {
                            super.shutdown();
                            SwingUtilities.invokeLater( new Runnable() {
                                public void run() {
                                    if ( tidier != null ) {
                                        tidier.tidyGui();
                                    }
                                }
                            } );
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
     * Constructs a mode for FacadeHubService.
     *
     * @return  mode based on the default client profile
     */
    private static HubServiceMode createFacadeHubMode( String name ) {
        return new HubServiceMode( name, true ) {
            HubService createHubService( Random random, HubProfile[] profiles,
                                         final Hub[] runners ) {
                return new FacadeHubService( DefaultClientProfile
                                            .getProfile() );
            }
        };
    }

    /**
     * HubServiceMode implementation for modes which cannot be used because they
     * rely on classes unavailable at runtime.
     */
    private static class BrokenHubMode extends HubServiceMode {
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
        HubService createHubService( Random random, HubProfile[] profiles,
                                     Hub[] runners ) {
            throw new RuntimeException( "Hub mode " + getName()
                                      + " unavailable", error_ );
        }
    }

    /**
     * Utility abstract class to define an object which can be tidied up
     * on hub shutdown.
     */
    private static abstract class Tidier {

        /**
         * Performs any required tidying operations.
         * May be assumed to be called on the AWT Event Dispatch Thread.
         */
        public abstract void tidyGui();
    }

    /**
     * Class to configure a window for use as a hub control.
     */
    private static class BasicWindowConfig extends Tidier {
        final JFrame frame_;
        final Hub[] runners_;
        final ProfileToggler[] profileTogglers_;
        final Action exitAct_;

        /**
         * Constructor.
         *
         * @param  frame  hub window
         * @param  profiles  hub profiles to run
         * @param  runners  1-element array which will contain an associated
         *         hub runner object if one exists
         */
        BasicWindowConfig( JFrame frame, HubProfile[] profiles,
                           final Hub[] runners ) {
            frame_ = frame;
            runners_ = runners;
            profileTogglers_ = new ProfileToggler[ profiles.length ];
            for ( int ip = 0; ip < profiles.length; ip++ ) {
                profileTogglers_[ ip ] =
                    new ProfileToggler( profiles[ ip ], runners );
            }
            exitAct_ = new AbstractAction( "Stop Hub" ) {
                public void actionPerformed( ActionEvent evt ) {
                    if ( runners[ 0 ] != null ) {
                        runners[ 0 ].shutdown();
                    }
                    tidyGui();
                }
            };
            exitAct_.putValue( Action.SHORT_DESCRIPTION,
                               "Shut down SAMP hub" );
            exitAct_.putValue( Action.MNEMONIC_KEY,
                               new Integer( KeyEvent.VK_T ) );
        }

        /**
         * Perform configuration of window.
         */
        public void configureWindow() {
            configureMenus();
            frame_.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
            frame_.setVisible( true );
            frame_.addWindowListener( new WindowAdapter() {
                public void windowClosed( WindowEvent evt ) {
                    Hub runner = runners_[ 0 ];
                    if ( runner != null ) {
                        runner.shutdown();
                    }
                }
            } );
        }

        /**
         * Configures menus on the window.  Invoked by configureWindow.
         */
        protected void configureMenus() {
            JMenuBar mbar = new JMenuBar();
            JMenu fileMenu = new JMenu( "File" );
            fileMenu.setMnemonic( KeyEvent.VK_F );
            fileMenu.add( new JMenuItem( exitAct_ ) );
            mbar.add( fileMenu );
            JMenu profileMenu = new JMenu( "Profiles" );
            profileMenu.setMnemonic( KeyEvent.VK_P );
            for ( int ip = 0; ip < profileTogglers_.length; ip++ ) {
                profileMenu.add( profileTogglers_[ ip ].createJMenuItem() );
            }
            mbar.add( profileMenu );
            frame_.setJMenuBar( mbar );
        }

        public void tidyGui() {
            if ( frame_.isDisplayable() ) {
                frame_.dispose();
            }
        }
    }

    /**
     * Takes care of hub display window configuration with system tray
     * functionality.
     */
    private static class SysTrayWindowConfig extends BasicWindowConfig {
        private final SysTray sysTray_;
        private final Action showAct_;
        private final Action hideAct_;
        private final MenuItem showItem_;
        private final MenuItem hideItem_;
        private final MenuItem exitItem_;
        private final ActionListener iconListener_;
        private Object trayIcon_;

        /**
         * Constructor.
         *
         * @param  frame  hub window
         * @param  profiles  hub profiles to run
         * @param  runners  1-element array which will contain an associated
         *         hub runner object if one exists
         * @param  sysTray  system tray facade object
         */
        SysTrayWindowConfig( JFrame frame, HubProfile[] profiles, Hub[] runners,
                             SysTray sysTray ) {
            super( frame, profiles, runners );
            sysTray_ = sysTray;
            showAct_ = new AbstractAction( "Show Hub Window" ) {
                public void actionPerformed( ActionEvent evt ) {
                    setWindowVisible( true );
                }
            };
            hideAct_ = new AbstractAction( "Hide Hub Window" ) {
                public void actionPerformed( ActionEvent evt ) {
                    setWindowVisible( false );
                }
            };
            showItem_ = toMenuItem( showAct_ );
            hideItem_ = toMenuItem( hideAct_ );
            exitItem_ = toMenuItem( exitAct_ );
            iconListener_ = showAct_;
        }

        protected void configureMenus() {
            super.configureMenus();
            frame_.getJMenuBar().getMenu( 0 ).add( new JMenuItem( hideAct_ ) );
        }

        public void configureWindow() {
            configureMenus();
            frame_.setDefaultCloseOperation( JFrame.HIDE_ON_CLOSE );

            // Arrange that a manual window close will set the action states
            // correctly.
            frame_.addWindowListener( new WindowAdapter() {
                public void windowClosing( WindowEvent evt ) {
                    showAct_.setEnabled( true );
                    hideAct_.setEnabled( false );
                }
            } );
            hideAct_.actionPerformed( null );
        }

        /**
         * Performs configuration.
         */
        public void configureSysTray() throws AWTException {
            Image im = Toolkit.getDefaultToolkit()
                      .createImage( Client.class
                                   .getResource( "images/hub.png" ) );
            String tooltip = "SAMP Hub";
            PopupMenu popup = new PopupMenu();
            Menu profileMenu = new Menu( "Profiles" );
            for ( int ip = 0; ip < profileTogglers_.length; ip++ ) {
                profileMenu.add( profileTogglers_[ ip ].createMenuItem() );
            }
            popup.add( profileMenu );
            popup.add( showItem_ );
            popup.add( hideItem_ );
            popup.add( exitItem_ );
            trayIcon_ = sysTray_.addIcon( im, tooltip, popup, iconListener_ );
        }

        public void tidyGui() {
            super.tidyGui();
            try {
                sysTray_.removeIcon( trayIcon_ );
            }
            catch ( AWTException e ) {
                logger_.warning( "Can't remove hub system tray icon: " + e );
            }
        }

        /**
         * Sets visibility for the hub control window, adjusting actions
         * as appropriate.
         *
         * @param  isVis  true for visible, false for invisible
         */
        private void setWindowVisible( boolean isVis ) {
            frame_.setVisible( isVis );
            showAct_.setEnabled( ! isVis );
            hideAct_.setEnabled( isVis );
            showItem_.setEnabled( ! isVis );
            hideItem_.setEnabled( isVis );
        }

        /**
         * Turns an action into an AWT menu item.
         *
         * @param  act  action
         * @return  MenuItem facade
         */
        private MenuItem toMenuItem( Action act ) {
            MenuItem item =
                new MenuItem( (String) act.getValue( Action.NAME ) );
            item.addActionListener( act );
            return item;
        }
    }

    /**
     * Manages a toggle button for starting/stopping profiles.
     * This object can supply both Swing JMenuItems and AWT MenuItems
     * with effectively the same model (which is quite hard work).
     */
    private static class ProfileToggler {
        final HubProfile profile_;
        final Hub[] runners_;
        final String title_;
        final JToggleButton.ToggleButtonModel toggleModel_;
        final List menuItemList_;

        /**
         * Constructor.
         *
         * @param  profile  profile to operate on
         * @param  runners  one-element array containing hub
         */
        ProfileToggler( HubProfile profile, Hub[] runners ) {
            profile_ = profile;
            runners_ = runners;
            title_ = profile.getProfileName() + " Profile";
            menuItemList_ = new ArrayList();
            toggleModel_ = new JToggleButton.ToggleButtonModel() {
                public boolean isSelected() {
                    return profile_.isRunning();
                }
                public void setSelected( boolean on ) {
                    Hub hub = runners_[ 0 ];
                    if ( hub != null ) {
                        if ( on && ! profile_.isRunning() ) {
                            try {
                                hub.startProfile( profile_ );
                                super.setSelected( on );
                            }
                            catch ( IOException e ) {
                                ErrorDialog
                               .showError( null, title_ + " Start Error",
                                           "Error starting " + title_, e );
                                return;
                            }
                        }
                        else if ( ! on && profile_.isRunning() ) {
                            hub.shutdownProfile( profile_ );
                        }
                    }
                    super.setSelected( on );
                }
            };
            toggleModel_.addChangeListener( new ChangeListener() {
                public void stateChanged( ChangeEvent evt ) {
                    updateMenuItems();
                }
            } );
        }

        /**
         * Returns a new Swing JMenuItem for start/stop toggle.
         *
         * @return menu item
         */
        public JMenuItem createJMenuItem() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem( title_ );
            item.setToolTipText( "Start or stop the " + title_ );
            char chr = Character
                      .toUpperCase( profile_.getProfileName().charAt( 0 ) );
            if ( chr >= 'A' && chr <= 'Z' ) {
                item.setMnemonic( (int) chr );
            }
            item.setModel( toggleModel_ );
            return item;
        }

        /**
         * Returns a new AWT MenuItem for start/stop toggle.
         *
         * @return  menu item
         */
        public MenuItem createMenuItem() {
            final CheckboxMenuItem item = new CheckboxMenuItem( title_ );
            item.addItemListener( new ItemListener() {
                 public void itemStateChanged( ItemEvent evt ) {
                     boolean on = item.getState();
                     toggleModel_.setSelected( on );
                     if ( toggleModel_.isSelected() != on ) {
                         item.setState( toggleModel_.isSelected() );
                     }
                 }
            } );
            item.setState( toggleModel_.isSelected() );
            menuItemList_.add( item );
            return item;
        }

        /**
         * Updates all dispatched menu items to the current state.
         */
        private void updateMenuItems() {
            for ( Iterator it = menuItemList_.iterator(); it.hasNext(); ) {
                CheckboxMenuItem item = (CheckboxMenuItem) it.next();
                boolean on = toggleModel_.isSelected();
                if ( item.getState() != on ) {
                    item.setState( on );
                }
            }
        }
    }
}
