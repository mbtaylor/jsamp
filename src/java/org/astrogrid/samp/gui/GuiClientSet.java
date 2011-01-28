package org.astrogrid.samp.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.hub.BasicClientSet;
import org.astrogrid.samp.hub.HubClient;

/**
 * ClientSet implementation used by GuiHubService.
 * It also implements {@link javax.swing.ListModel}.
 *
 * @author   Mark Taylor
 * @since    20 Nov 2008
 */
class GuiClientSet extends BasicClientSet implements ListModel {

    private final List clientList_;
    private final List listenerList_;
    private final static HubClient MORIBUND_CLIENT = new HubClient( "<no-id>" );

    /**
     * Constructor.
     *
     * @param  clientIdComparator  comparator for client IDs
     */
    public GuiClientSet( Comparator clientIdComparator ) {
        super( clientIdComparator );
        clientList_ = Collections.synchronizedList( new ArrayList() );
        listenerList_ = new ArrayList();
    }

    public synchronized void add( HubClient client ) {
        super.add( client );
        int index = clientList_.size();
        clientList_.add( client );
        scheduleListDataEvent( ListDataEvent.INTERVAL_ADDED, index, index );
    }

    public synchronized void remove( HubClient client ) {
        super.remove( client );
        clientList_.remove( client );
        int index = clientList_.size();
        scheduleListDataEvent( ListDataEvent.INTERVAL_REMOVED, index, index );
    }

    public synchronized HubClient[] getClients() {
        return (HubClient[]) clientList_.toArray( new HubClient[ 0 ] );
    }

    public Object getElementAt( int index ) {
        try {
            return clientList_.get( index );
        }

        // May be called from other than the event dispatch thread.
        catch ( IndexOutOfBoundsException e ) {
            return MORIBUND_CLIENT;
        }
    }

    public int getSize() {
        return clientList_.size();
    }

    public void addListDataListener( ListDataListener l ) {
        listenerList_.add( l ); 
    }

    public void removeListDataListener( ListDataListener l ) {
        listenerList_.remove( l );
    }

    /**
     * Schedules notification of list data listeners that the attributes
     * of a client have changed.
     * May be called from any thread.
     *
     * @param  client  client which has changed
     */
    public synchronized void scheduleClientChanged( HubClient client ) {
        for ( int ix = 0; ix < clientList_.size(); ix++ ) {
            if ( clientList_.get( ix ).equals( client ) ) {
                scheduleListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                                       ix, ix );
                return;
            }
        }
        scheduleListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                               0, clientList_.size() );
    }

    /**
     * Schedules notification of list data listeners about an event.
     * May be called from any thread.
     *
     * @param  type  ListDataEvent event type
     * @param  int  index0  ListDataEvent start index
     * @param  int  index1  ListDataEvent end index
     */
    private void scheduleListDataEvent( int type, int index0, int index1 ) {
        if ( ! listenerList_.isEmpty() ) {
            final ListDataEvent evt =
                new ListDataEvent( this, type, index0, index1 );
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    fireListDataEvent( evt );
                } 
            } );
        }
    }

    /**
     * Passes a ListDataEvent to all listeners.
     * Must be called from AWT event dispatch thread.
     *
     * @param  evt  event to forward
     */
    public void fireListDataEvent( ListDataEvent evt ) {
        assert SwingUtilities.isEventDispatchThread();
        int type = evt.getType();
        for ( Iterator it = listenerList_.iterator(); it.hasNext(); ) {
            ListDataListener listener = (ListDataListener) it.next();
            if ( type == ListDataEvent.INTERVAL_ADDED ) {
                listener.intervalAdded( evt );
            }
            else if ( type == ListDataEvent.INTERVAL_REMOVED ) {
                listener.intervalRemoved( evt );
            }
            else if ( type == ListDataEvent.CONTENTS_CHANGED ) {
                listener.contentsChanged( evt );
            }
            else {
                assert false;
            }
        }
    }
}
