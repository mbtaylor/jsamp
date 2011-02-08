package org.astrogrid.samp.hub;

import java.io.IOException;
import java.util.List;

/**
 * Factory to produce hub profiles of a particular type.
 * Used with the command-line invocation of the hub.
 *
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public interface HubProfileFactory {

    /**
     * Returns the name used to identify this profile.
     *
     * @return  short name
     */
    String getName();

    /**
     * Returns an array of strings, each describing one command-line flag
     * which will be consumed by the <code>createProfile</code> method.
     *
     * @return  array of plain-text strings suitable for use as part of
     *          a usage message
     */
    String[] getFlagsUsage();

    /**
     * Creates a HubProfile perhaps configured using a supplied list
     * of flags.  Any flags which match those described by the
     * {@link #getFlagsUsage} command are used for configuration of the
     * returned hub, and must be removed from the <code>flagList</code> list.
     * Unrecognised flags should be ignored and left in the list.
     * Flags which are recognised but badly formed should raise a
     * RuntimeException with a helpful message.
     *
     * @param  flagList  mutable list of Strings giving command-ilne flags,
     *         some of which may be intended for configuring a profile
     * @return  new profile
     */
    HubProfile createHubProfile( List flagList ) throws IOException;

    /**
     * Returns a HubProfile subclass with a no-arg constructor which,
     * when invoked, will produce a basic instance of the HubProfile
     * represented by this factory.  The instance thus produced will
     * typically be similar to that produced by invoking
     * {@link #createHubProfile} with an empty flag list.
     *
     * @return   HubProfile subclass with a public no-arg constructor
     */
    Class getHubProfileClass();
}
