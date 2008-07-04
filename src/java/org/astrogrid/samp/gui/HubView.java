package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.astrogrid.samp.Client;

public class HubView extends JPanel {

    private final JList jlist_;
    private final ClientPanel clientPanel_;
    private final ListDataListener listListener_;

    public HubView() {
        super( new BorderLayout() );

        jlist_ = new JList();
        ListSelectionModel selModel = jlist_.getSelectionModel();
        selModel.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        selModel.addListSelectionListener( new ListSelectionListener() {
            public void valueChanged( ListSelectionEvent evt ) {
                if ( ! evt.getValueIsAdjusting() ) {
                    updateClientView();
                }
            }
        } );

        listListener_ = new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                int isel = jlist_.getSelectedIndex();
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( isel >= 0 && ( i0 < 0 || i1 < 0 ||
                                    ( i0 - isel ) * ( i1 - isel ) <= 0 ) ) {
                    updateClientView();
                }
            }
            public void intervalRemoved( ListDataEvent evt ) {
                if ( clientPanel_.getClient() != null &&
                     jlist_.getSelectedIndex() < 0 ) {
                    updateClientView();
                }
            }
            public void intervalAdded( ListDataEvent evt ) {
            }
        };

        clientPanel_ = new ClientPanel();

        JSplitPane splitter = new JSplitPane();
        JScrollPane listScroller = new JScrollPane( jlist_ );
        listScroller.setPreferredSize( new Dimension( 120, 500 ) );
        splitter.setLeftComponent( listScroller );
        splitter.setRightComponent( clientPanel_ );
        add( splitter );
    }

    public void setClientListModel( ListModel clientModel ) {
        ListModel oldModel = jlist_.getModel();
        jlist_.getSelectionModel().clearSelection();
        if ( oldModel != null ) {
            oldModel.removeListDataListener( listListener_ );
        }
        jlist_.setModel( clientModel );
        if ( clientModel != null ) {
            clientModel.addListDataListener( listListener_ );
            jlist_.setCellRenderer( new ClientListCellRenderer( clientModel,
                                                                null ) );
        }
    }

    private void updateClientView() {
        int isel = jlist_.getSelectedIndex();
        clientPanel_.setClient( isel >= 0
                              ? (Client) jlist_.getModel().getElementAt( isel )
                              : null );
    }
}
