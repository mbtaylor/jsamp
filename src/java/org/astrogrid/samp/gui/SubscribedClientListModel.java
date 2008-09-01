package org.astrogrid.samp.gui;

import org.astrogrid.samp.Client;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.SampException;

/**
 * Selective client list model which contains only those non-self clients
 * which are subscribed to one or more of a given list of MTypes.
 *
 * @author   Mark Taylor
 * @since    1 Sep 2008
 */
public class SubscribedClientListModel extends SelectiveClientListModel {

    private final HubConnector connector_;
    private final String[] mtypes_;

    /**
     * Constructor for multiple MTypes.
     *
     * @param  connector   hub connector
     * @param  mtypes   mtypes of interest (may have wildcards)
     */
    public SubscribedClientListModel( HubConnector connector,
                                      String[] mtypes ) {
        super( connector.getClientListModel() );
        connector_ = connector;
        mtypes_ = (String[]) mtypes.clone();
        init();
    }

    /**
     * Constructor for single MType.
     *
     * @param  connector   hub connector
     * @param  mtype   mtype of interest (may have wildcards)
     */
    public SubscribedClientListModel( HubConnector connector, String mtype ) {
        this( connector, new String[] { mtype } );
    }

    protected boolean isIncluded( Client client ) {
        String selfId;
        try { 
            selfId = connector_.getConnection().getRegInfo().getSelfId();
        }
        catch ( SampException e ) {
            return false;
        }
        if ( client.getId().equals( selfId ) ) {
            return false;
        }
        Subscriptions subs = client.getSubscriptions();
        if ( subs != null ) {
            for ( int im = 0; im < mtypes_.length; im++ ) {
                if ( subs.isSubscribed( mtypes_[ im ] ) ) {
                    return true;
                }
            }
        }
        return false;
    }
}
