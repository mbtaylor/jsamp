package org.astrogrid.samp.hub;

import javax.swing.JToggleButton;

/**
 * Marks a HubProfile that can also provide GUI-based configuration.
 * This is a bit of a hack, in that it's not very general; it is just
 * intended at present for the WebHubProfile and is rather specific to
 * its needs.  This interface may change or disappear at some point
 * in the future.
 *
 * @author   Mark Taylor
 * @since    22 Jul 2011
 */
public interface ConfigHubProfile extends HubProfile {

    /**
     * Returns some toggle button models for hub profile configuration.
     *
     * @return  toggle button model array
     */
    JToggleButton.ToggleButtonModel[] getConfigModels();
}
