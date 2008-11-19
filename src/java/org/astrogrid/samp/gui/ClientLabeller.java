package org.astrogrid.samp.gui;

import org.astrogrid.samp.Client;

/**
 * Provides text label for a {@link org.astrogrid.samp.Client}.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public interface ClientLabeller {

    /**
     * Attempts to return a human-readable text label for the given client.
     *
     * @param   client  to find label for
     * @return  human-readable label for client if available; if nothing
     *          better than the public ID can be found, null is returned
     */
    String getLabel( Client client );
}
