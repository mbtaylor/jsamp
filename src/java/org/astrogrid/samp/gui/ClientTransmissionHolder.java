package org.astrogrid.samp.gui;

import javax.swing.ListModel;
import org.astrogrid.samp.Client;

/**
 * Provides the means to obtain list models containing pending sent and
 * received transmissions.
 *
 * @author   Mark Taylor
 * @since    26 Nov 2008
 */
interface ClientTransmissionHolder {

    /**
     * Returns a list model containing messages sent by a given client.
     *
     * @return   list model containing {@link Transmission} objects
     */
    ListModel getTxListModel( Client client );

    /**
     * Returns a list model containing messages received by a given client.
     *
     * @return   list model containing {@link Transmission} objects
     */
    ListModel getRxListModel( Client client );
}
