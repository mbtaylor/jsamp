package org.astrogrid.samp.gui;

import org.astrogrid.samp.Client;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * Selective client list model which contains only those non-self clients
 * which are subscribed to one or more of a given list of MTypes.
 *
 * @author   Mark Taylor
 * @since    1 Sep 2008
 */
public class SubscribedClientListModel extends SelectiveClientListModel {

    private final GuiHubConnector connector_;
    private String[] mtypes_;

    /**
     * Constructor for multiple MTypes.
     *
     * @param  connector   hub connector
     * @param  mtypes   mtypes of interest (may have wildcards)
     */
    public SubscribedClientListModel( GuiHubConnector connector,
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
    public SubscribedClientListModel( GuiHubConnector connector,
                                      String mtype ) {
        this( connector, new String[] { mtype } );
    }

    /**
     * Sets the list of MTypes which defines the elements of this list.
     * Any client subscribed to one or more of these MTypes is included.
     *
     * @param   mtypes  new MType list
     */
    public void setMTypes( String[] mtypes ) {
        mtypes_ = (String[]) mtypes.clone();
        refresh();
        fireContentsChanged( this, -1, -1 );
    }

    /**
     * Returns the list of MTypes which defines the elements of this list.
     *
     * @return  MType list
     */
    public String[] getMTypes() {
        return mtypes_;
    }

    /**
     * Returns true if <code>client</code> is subscribed to one of this
     * model's MTypes.
     */
    protected boolean isIncluded( Client client ) {
        String selfId;
        try { 
            HubConnection connection = connector_.getConnection();
            if ( connection == null ) {
                return false;
            }
            else {
                selfId = connection.getRegInfo().getSelfId();
            }
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
