package org.astrogrid.samp.gui;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;

/**
 * Provides text label for a {@link org.astrogrid.samp.Client}.
 * This object maintains internal state so that it can render clients
 * in an appropriately consistent way.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class ClientLabeller implements ListDataListener {

    private final ListModel clientList_;
    private final RegInfo regInfo_;
    private final Map clientMap_;
    private final Map nameCountMap_;

    /**
     * Constructor.
     * If the <code>clientList</code> argument is non-null, this object
     * will register as a listener to it.
     * Both arguments are used to try to get more human-friendly labels
     * for the clients, but may be null.
     *
     * @param  clientList  listmodel from which Clients will be drawn;
     *                     may be null
     * @param  regInfo   registration information for client hub connection;
     *                   may be null
     */
    public ClientLabeller( ListModel clientList, RegInfo regInfo ) {
        clientList_ = clientList;
        regInfo_ = regInfo;
        clientMap_ = new HashMap();
        nameCountMap_ = new HashMap();
        if ( clientList_ != null ) {
            clientList_.addListDataListener( this );
            refreshClients();
        }
    }

    /**
     * Attempts to return a human-readable text label for the given client.
     *
     * @param   client  to find label for
     * @return  human-readable label for client if available; if nothing
     *          better than the public ID can be found, null is returned
     */
    public String getLabel( Client client ) {
        return getClientInfo( client ).getLabel();
    }

    /**
     * Returns the ClientInfo object corresponding to a given client.
     * Creates a new one if none is known.
     *
     * @param  client  client
     * @return  new or existing ClientInfo
     */
    private ClientInfo getClientInfo( Client client ) {
        ClientInfo info = (ClientInfo) clientMap_.get( client );
        if ( info == null ) {
            info = new ClientInfo( client );
            clientMap_.put( client, info );
        }
        return info;
    }

    public void contentsChanged( ListDataEvent evt ) {
        int i0 = evt.getIndex0();
        int i1 = evt.getIndex1();
        if ( i0 >= 0 && i1 >= i0 ) {
            for ( int i = i0; i <= i1; i++ ) {
                updateClient( (Client) clientList_.getElementAt( i ) );
            }
        }
        else {
            refreshClients();
            tidyClients();
        }
    }

    public void intervalAdded( ListDataEvent evt ) {
        int i0 = evt.getIndex0();
        int i1 = evt.getIndex1();
        for ( int i = i0; i0 <= i1; i0++ ) {
            updateClient( (Client) clientList_.getElementAt( i ) );
        }
    }

    public void intervalRemoved( ListDataEvent evt ) {
        tidyClients();
    }

    private void updateClient( Client client ) {
        getClientInfo( client ).updateMetadata();
    }

    /**
     * Clear out state relating to clients which are no longer in the 
     * list.
     */
    private void tidyClients() {
        Collection activeSet = new HashSet();
        for ( int i = 0; i < clientList_.getSize(); i++ ) {
            activeSet.add( clientList_.getElementAt( i ) );
        }
        clientMap_.keySet().retainAll( activeSet );
        if ( clientMap_.isEmpty() ) {
            nameCountMap_.clear();
        }
    }

    /**
     * Make sure that all labelling information is up to date.
     */
    private void refreshClients() {
        for ( int i = 0; i < clientList_.getSize(); i++ ) {
            updateClient( (Client) clientList_.getElementAt( i ) );
        }
    }

    /**
     * Utility class which stores information about clients we have come
     * across before.
     */
    private class ClientInfo {
        private final Client client_;
        private final String role_;
        private String name_;
        private int nameSeq_;
        private String label_;

        /**
         * Constructor.
         *
         * @param  client
         */
        ClientInfo( Client client ) {
            client_ = client;
            if ( regInfo_ != null ) {
                String id = client.getId();
                if ( id.equals( regInfo_.getSelfId() ) ) {
                    role_ = "self";
                }
                else if ( id.equals( regInfo_.getHubId() ) ) {
                    role_ = "hub";
                }
                else {
                    role_ = null;
                }
            }
            else {
                role_ = null;
            }
            updateMetadata();
        }

        /**
         * Label associated with this client.
         *
         * @return label
         */
        public String getLabel() {
            return label_;
        }

        /**
         * Call when the metadata of this client might have changed.
         */
        public void updateMetadata() {
            Metadata meta = client_.getMetadata();
            if ( meta != null ) {
                String name = meta.getName();
                if ( name != null && ! name.equals( name_ ) ) {
                    name_ = name;
                    int nameCount = nameCountMap_.containsKey( name )
                           ? ((Number) nameCountMap_.get( name )).intValue()
                           : 0;
                    nameSeq_ = nameCount;
                    nameCountMap_.put( name, new Integer( nameCount + 1 ) );
                }
            }
            StringBuffer lbuf = new StringBuffer();
            if ( name_ != null ) {
                lbuf.append( name_ );
                if ( nameSeq_ > 0 ) {
                    lbuf.append( '-' )
                        .append( nameSeq_ );
                }
                if ( role_ != null ) {
                    lbuf.append( ' ' );
                }
            }
            if ( role_ != null ) {
                lbuf.append( '(' )
                    .append( role_ )
                    .append( ')' );
            }
            label_ = lbuf.length() > 0 ? lbuf.toString() : null;
        }
    }
}
