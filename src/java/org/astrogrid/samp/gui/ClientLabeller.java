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

public class ClientLabeller implements ListDataListener {

    private final Map clientLabelMap_;
    private final Map clientIconMap_;
    private final Map nameCountMap_;
    private final Map urlIconMap_;
    private ListModel clientList_;

    public ClientLabeller() {
        clientLabelMap_ = new HashMap();
        clientIconMap_ = new HashMap();
        nameCountMap_ = new HashMap();
        urlIconMap_ = new HashMap();
    }

    public void setClientListModel( ListModel clientList ) {
        if ( clientList_ != null ) {
            clientList_.removeListDataListener( this );
        }
        clientList_ = clientList;
        if ( clientList_ != null ) {
            clientList_.addListDataListener( this );
        }
        refreshClients();
    }

    public String getLabel( Client client ) {
        return (String) clientLabelMap_.get( client );
    }

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

    private String generateLabel( Client client ) {
        Metadata meta = client.getMetadata();
        if ( meta != null ) {
            String name = meta.getName();
            if ( name != null ) {
                int nameCount = nameCountMap_.containsKey( name )
                              ? ((Number) nameCountMap_.get( name )).intValue()
                              : 0;
                nameCountMap_.put( name, new Integer( nameCount + 1 ) );
                return nameCount == 0 ? name
                                      : name + "-" + nameCount;
            }
        }
        return null;
    }

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
}
