package org.astrogrid.samp;

import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles ordered running of cleanup processes at JVM shutdown.
 * This is a singleton class, use {@link #getInstance}.
 *
 * @author  Sylvain Lafrasse
 * @author  Mark Taylor
 * @since   12 Oct 2012
 */
public class ShutdownManager {

    /** Shutdown sequence value for client hooks. */
    public static final int CLIENT_SEQUENCE = 100;

    /** Shutdown sequence value for hub hooks. */
    public static final int HUB_SEQUENCE = 200;

    private static final ShutdownManager instance_ = new ShutdownManager();
    private static final Logger logger_ =
        Logger.getLogger( "org.astrogrid.samp" );


    /** Maps Objects to Hooks. */
    private final WeakHashMap hookMap_;

    /**
     * Private constructor prevents instantiation.
     */
    private ShutdownManager() {
        hookMap_ = new WeakHashMap();
        try {
            Runtime.getRuntime()
                   .addShutdownHook( new Thread( "SAMP Shutdown" ) {
                public void run() {
                    doCleanup();
                }
            } );
        }
        catch ( SecurityException e ) {
            logger_.log( Level.WARNING, "Can't add shutdown hook: " + e, e );
        }
    }

    /**
     * Register a runnable to be run on shutdown with a given key and sequence.
     * Items with a smaller value of <code>iseq</code>
     * are run earlier at shutdown.
     * Suitable sequence values are given by {@link #HUB_SEQUENCE} and
     * {@link #CLIENT_SEQUENCE}.
     * The <code>key</code> is kept in a WeakHashMap, so if it is GC'd,
     * the runnable will never execute.
     *
     * @param   key   key which can be used to unregister the hook later
     * @param   iseq  value indicating position in shutdown sequence
     * @param   runnable  to be run on shutdown
     */
    public synchronized void registerHook( Object key, int iseq,
                                           Runnable runnable ) {
        hookMap_.put( key, new Hook( runnable, iseq ) );
    }

    /**
     * Unregisters a key earlier registered using {@link #registerHook}.
     *
     * @param  key  registration key
     */
    public synchronized void unregisterHook( Object key ) {
        hookMap_.remove( key );
    }

    /**
     * Invoked on shutdown by runtime.
     */
    private void doCleanup() {
        Hook[] hooks;
        synchronized ( this ) {
            hooks = (Hook[]) hookMap_.values().toArray( new Hook[ 0 ] );
        }
        Arrays.sort( hooks );
        logger_.info( "SAMP shutdown start" );
        for ( int ih = 0; ih < hooks.length; ih++ ) {
            try {
                hooks[ ih ].runnable_.run();
            }
            catch ( RuntimeException e ) {
                forceLog( logger_, Level.WARNING, "Shutdown hook failure: " + e,
                          e );
            }
        }
        logger_.info( "SAMP shutdown end" );
    }

    /**
     * Returns sole instance of this class.
     *
     * @return instance
     */
    public static ShutdownManager getInstance() {
        return instance_;
    }

    /**
     * Writes a log-like message directly to standard error if it has
     * an appropriate level.
     * This method is only intended for use during the shutdown process,
     * when the logging system may be turned off so that normal logging
     * calls may get ignored (this behaviour is not as far as I know
     * documented, but seems reliable in for example Oracle JRE1.5).
     * There may be some good reason for logging services to be withdrawn
     * during shutdown, so it's not clear that using this method is
     * a good idea at all even apart from bypassing the logging system;
     * therefore use it sparingly.
     *
     * @param   logger  logger
     * @param   level   level of message to log
     * @param   msg    text of logging message
     * @param   error  associated throwable if any; may be null
     */
    public static void forceLog( Logger logger, Level level, String msg,
                                 Throwable error ) {
        if ( logger.isLoggable( level ) ) {
            System.err.println( level + ": " + msg );
            if ( error != null ) {
                error.printStackTrace( System.err );
            }
        }
    }

    /**
     * Aggregates a runnable and an associated sequence value.
     */
    private static class Hook implements Comparable {
        final Runnable runnable_;
        final int iseq_;

        /**
         * Constructor.
         *
         * @param  runnable   runnable
         * @param  iseq       sequence value
         */
        Hook( Runnable runnable, int iseq ) {
            runnable_ = runnable;
            iseq_ = iseq;
        }

        public int compareTo( Object other ) {
            return this.iseq_ - ((Hook) other).iseq_;
        }
    }
}
