package org.astrogrid.samp.hub;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Basic ClientSet implementation.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
public class BasicClientSet implements ClientSet {

    private final Map publicIdMap_;

    /**
     * Constructor.
     *
     * @param  clientIdComparator  comparator for client IDs
     */
    public BasicClientSet( Comparator clientIdComparator ) {
        publicIdMap_ = Collections
                      .synchronizedMap( new TreeMap( clientIdComparator ) );
    }

    public synchronized void add( HubClient client ) {
        publicIdMap_.put( client.getId(), client );
    }

    public synchronized void remove( HubClient client ) {
        publicIdMap_.remove( client.getId() );
    }

    public synchronized HubClient getFromPublicId( String publicId ) {
        return (HubClient) publicIdMap_.get( publicId );
    }

    public synchronized HubClient[] getClients() {
        return (HubClient[])
               publicIdMap_.values().toArray( new HubClient[ 0 ] );
    }

    public synchronized boolean containsClient( HubClient client ) {
        return publicIdMap_.containsValue( client );
    }
}
