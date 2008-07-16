package org.astrogrid.samp.gui;

import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;

/**
 * Provides text and icon labels for a {@link org.astrogrid.samp.Client}.
 * This object maintains internal state so that it can render clients 
 * in an appropriately consistent way.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class ClientLabeller implements ListDataListener {

    private final Map clientLabelMap_;
    private final Map clientIconMap_;
    private final Map nameCountMap_;
    private final Map urlIconMap_;
    private final ListModel clientList_;
    private final RegInfo regInfo_;

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
        clientLabelMap_ = new HashMap();
        clientIconMap_ = new HashMap();
        nameCountMap_ = new HashMap();
        urlIconMap_ = new HashMap();
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
        return (String) clientLabelMap_.get( client );
    }

    /**
     * Attempts to return an icon associated with the given client.
     *
     * @param  client to find icon for
     * @return  icon if known
     */
    public Icon getIcon( Client client ) {
        return (Icon) clientIconMap_.get( client );
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
        clientLabelMap_.put( client, generateLabel( client ) );
        clientIconMap_.put( client, generateIcon( client ) );
    }

    private void tidyClients() {
        Collection activeSet = new HashSet();
        for ( int i = 0; i < clientList_.getSize(); i++ ) {
            activeSet.add( clientList_.getElementAt( i ) );
        }
        clientLabelMap_.keySet().retainAll( activeSet );
    }

    private void refreshClients() {
        for ( int i = 0; i < clientList_.getSize(); i++ ) {
            updateClient( (Client) clientList_.getElementAt( i ) );
        }
        tidyClients();
    }

    /**
     * Attempts to work out an icon associated with the given client.
     *
     * @param  client to find icon for
     * @return  icon if known
     */
    private Icon generateIcon( Client client ) {
        Metadata meta = client.getMetadata();
        if ( meta != null ) {
            URL url = meta.getIconUrl();
            if ( url != null ) {
                if ( ! urlIconMap_.containsKey( url ) ) {
                    urlIconMap_.put( url, new ImageIcon( url ) );
                }
                return (Icon) urlIconMap_.get( url );
            }
        }
        return null;
    }

    /**
     * Attempts to work out a human-readable text label for the given client.
     *
     * @param   client  to find label for
     * @return  human-readable label for client if available; if nothing
     *          better than the public ID can be found, null is returned
     */
    private String generateLabel( Client client ) {
        String name = generateName( client );
        String role = generateRole( client );
        if ( name != null ) {
            return role == null
                 ? name
                 : name + " (" + role + ")";
        }
        else if ( role != null ) {
            return "(" + role + ")";
        }
        else {
            return null;
        }
    }

    /**
     * Returns a name based on the client metadata if possible.
     * This is disambiguated with a number if the client list contains
     * (or has contained) multiple clients with the same name.
     *
     * @param  client  client
     * @return   qualified client name
     */
    private String generateName( Client client ) {
        Metadata meta = client.getMetadata();
        if ( meta != null ) {
            String name = meta.getName();
            if ( name != null ) {
                int nameCount = nameCountMap_.containsKey( name )
                              ? ((Number) nameCountMap_.get( name )).intValue()
                              : 0;
                nameCountMap_.put( name, new Integer( nameCount + 1 ) );
                if ( nameCount > 0 ) {
                    name += "-" + nameCount;
                }
            }
            return name;
        }
        else {
            return null;
        }
    }

    /**
     * Returns a special client role if appropriate.
     *
     * @return "self", "hub" or null
     */
    private String generateRole( Client client ) {
        String id = client.getId();
        if ( id.equals( regInfo_.getSelfId() ) ) {
            return "self";
        }
        else if ( id.equals( regInfo_.getHubId() ) ) {
            return "hub";
        }
        else {
            return null;
        }
    }
}
