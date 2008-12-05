package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Displays a set of transmissions in a table model,
 * along with a detail panel for the selected one.
 *
 * @author    Mark Taylor
 * @since     5 Dec 2008
 */
class TransmissionView extends JPanel {

    /**
     * Constructor.
     *
     * @param   transModel   table model containing transmissions
     */
    public TransmissionView( final TransmissionTableModel transModel ) {
        super( new BorderLayout() );
        final TransmissionPanel transPanel = new TransmissionPanel();
        transPanel.setBorder( BorderFactory
                             .createBevelBorder( BevelBorder.LOWERED ) );
        final JTable table = new JTable( transModel );
        Dimension tableSize = table.getPreferredScrollableViewportSize();
        tableSize.height = 80;
        table.setPreferredScrollableViewportSize( tableSize );
        final ListSelectionModel selModel = table.getSelectionModel();
        selModel.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
        selModel.addListSelectionListener( new ListSelectionListener() {
            public void valueChanged( ListSelectionEvent evt ) {
                Object src = evt.getSource();
                if ( ! selModel.getValueIsAdjusting() && 
                     ! selModel.isSelectionEmpty() ) {
                    Transmission trans =
                        transModel.getTransmission( table.getSelectedRow() );
                    transPanel.setTransmission( trans );
                }
            }
        } );
        JSplitPane splitter = new JSplitPane( JSplitPane.VERTICAL_SPLIT );
        add( splitter, BorderLayout.CENTER );
        splitter.setTopComponent( new JScrollPane( table ) );
        splitter.setBottomComponent( transPanel );
        Dimension splitSize = splitter.getPreferredSize();
        splitSize.height = 180;
        splitter.setPreferredSize( splitSize );
    }
}
