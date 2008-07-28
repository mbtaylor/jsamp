package org.astrogrid.samp.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.hub.BasicHubService;
import org.astrogrid.samp.hub.ClientSet;
import org.astrogrid.samp.hub.HubClient;
import org.astrogrid.samp.hub.HubServiceException;

/**
 * BasicHubService subclass which provides a GUI window displaying hub
 * status as well as the basic hub services.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class GuiHubService extends BasicHubService {

    private final GuiClientSet clientSet_;
    private final static HubClient MORIBUND_CLIENT =
         new HubClient( "<no-id>", "<moribund>" );

    /**
     * Constructor.
     *
     * @param  random  random number generator used for private keys etc
     */
    public GuiHubService( Random random ) {
        super( random );
        clientSet_ = new GuiClientSet( super.getClientSet() );
    }

    public void start() {
        super.start();
    }

    /**
     * Creates a new window which maintains a display of the current hub
     * internal state.
     *
     * @return   new hub viewer window
     */
    public JFrame createHubWindow() {
        HubView hubView = new HubView();
        hubView.setClientListModel( getClientListModel() );
        JFrame frame = new JFrame( "SAMP Hub" );
        frame.getContentPane().add( hubView );
        frame.setIconImage( new ImageIcon( Client.class
                                          .getResource( "images/hub.png" ) )
                           .getImage() );
        frame.pack();
        return frame;
    }

    protected ClientSet getClientSet() {
        return clientSet_;
    }

    public void declareMetadata( Object id, Map meta )
            throws HubServiceException {
        super.declareMetadata( id, meta );
        clientSet_.scheduleClientChanged( (String) id );
    }

    public void declareSubscriptions( Object id, Map subscriptions )
            throws HubServiceException {
        super.declareSubscriptions( id, subscriptions );
        clientSet_.scheduleClientChanged( (String) id );
    }

    /**
     * Returns a ListModel containing information about clients currently 
     * registered with this hub.
     *
     * @return   list model in which each element is a 
     *           {@link org.astrogrid.samp.Client}
     */
    public ListModel getClientListModel() {
        return clientSet_;
    }

    /**
     * ClientSet implementation used by this class.
     * It also implements {@link javax.swing.ListModel}.
     * It is based on an existing <code>ClientSet</code>
     */
    private static class GuiClientSet implements ClientSet, ListModel {

        private final ClientSet base_;
        private final List clientList_;
        private final List listenerList_;

        /**
         * Constructor.
         *
         * @param  base  client set providing base functionality
         */
        public GuiClientSet( ClientSet base ) {
            base_ = base;
            clientList_ = Collections.synchronizedList( new ArrayList() );
            listenerList_ = new ArrayList();
        }

        public synchronized void add( HubClient client ) {
            base_.add( client );
            int index = clientList_.size();
            clientList_.add( client );
            scheduleListDataEvent( ListDataEvent.INTERVAL_ADDED,
                                   index, index );
        }

        public synchronized void remove( HubClient client ) {
            base_.remove( client );
            clientList_.remove( client );
            int index = clientList_.size();
            scheduleListDataEvent( ListDataEvent.INTERVAL_REMOVED,
                                   index, index );
        }

        public HubClient getFromPublicId( String publicId ) {
            return base_.getFromPublicId( publicId );
        }

        public HubClient getFromPrivateKey( String privateKey ) {
            return base_.getFromPrivateKey( privateKey );
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
                        fireEvent( evt );
                    }
                } );
            }
        }

        /**
         * Schedules notification of list data listeners that the attributes
         * of a client have changed.
         * May be called from any thread.
         *
         * @param  privateKey  private key of client which has changed
         */
        synchronized void scheduleClientChanged( String privateKey ) {
            for ( int ix = 0; ix < clientList_.size(); ix++ ) {
                HubClient client = (HubClient) clientList_.get( ix );
                if ( client.getPrivateKey().equals( privateKey ) ) {
                    scheduleListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                                           ix, ix );
                    return;
                }
            }
            scheduleListDataEvent( ListDataEvent.CONTENTS_CHANGED,
                                   0, clientList_.size() );
        }

        /**
         * Passes a ListDataEvent to all listeners.
         * Must be called from AWT event dispatch thread.
         *
         * @param  evt  event to forward
         */
        private void fireEvent( ListDataEvent evt ) {
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
}
