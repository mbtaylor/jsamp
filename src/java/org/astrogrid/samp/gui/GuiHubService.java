package org.astrogrid.samp.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractListModel;
import javax.swing.JFrame;
import javax.swing.ListModel;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.hub.ClientSet;
import org.astrogrid.samp.hub.HubClient;
import org.astrogrid.samp.hub.BasicHubService;

public class GuiHubService extends BasicHubService {

    private final GuiClientSet clientSet_;

    public GuiHubService() {
        clientSet_ = new GuiClientSet( super.getClientSet() );
    }

    public void start() {
        super.start();
    }

    public JFrame createHubWindow() {
        HubView hubView = new HubView();
        hubView.setClientList( getClientListModel() );
        JFrame frame = new JFrame( "Hub" );
        frame.getContentPane().add( hubView );
        frame.pack();
        return frame;
    }

    protected ClientSet getClientSet() {
        return clientSet_;
    }

    public void declareMetadata( Object id, Map meta ) throws SampException {
        super.declareMetadata( id, meta );
        clientSet_.fireClientChanged( (String) id );
    }

    public void declareSubscriptions( Object id, Map subscriptions )
            throws SampException {
        super.declareSubscriptions( id, subscriptions );
        clientSet_.fireClientChanged( (String) id );
    }

    public ListModel getClientListModel() {
        return clientSet_;
    }

    private static class GuiClientSet extends AbstractListModel
                                      implements ClientSet {

        private final ClientSet base_;
        private final List clientList_;

        public GuiClientSet( ClientSet base ) {
            base_ = base;
            clientList_ = Collections.synchronizedList( new ArrayList() );
        }

        public synchronized void add( HubClient client ) {
            base_.add( client );
            int index = clientList_.size();
            clientList_.add( client );
            fireIntervalAdded( this, index, index );
        }

        public synchronized void remove( HubClient client ) {
            base_.remove( client );
            clientList_.remove( client );
            int index = clientList_.size();
            fireIntervalRemoved( this, index, index );
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
            return clientList_.get( index );
        }

        public int getSize() {
            return clientList_.size();
        }

        synchronized void fireClientChanged( String privateKey ) {
            for ( int ix = 0; ix < clientList_.size(); ix++ ) {
                HubClient client = (HubClient) clientList_.get( ix );
                if ( client.getPrivateKey().equals( privateKey ) ) {
                    fireContentsChanged( this, ix, ix );
                    return;
                }
            }
            fireContentsChanged( this, 0, clientList_.size() );
        }
    }
}
