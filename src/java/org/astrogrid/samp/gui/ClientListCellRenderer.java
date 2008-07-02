package org.astrogrid.samp.gui;

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;

public class ClientListCellRenderer extends DefaultListCellRenderer {

    private final ClientLabeller labeller_;
    private ListModel listModel_;

    public ClientListCellRenderer() {
        labeller_ = new ClientLabeller();
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( c instanceof JLabel && value instanceof Client ) {
            JLabel jl = (JLabel) c;
            Client client = (Client) value;
            ListModel model = list.getModel();
            if ( model != listModel_ ) {
                labeller_.setClientListModel( model );
                listModel_ = model;
            }
            jl.setText( labeller_.getLabel( client ) );
            jl.setIcon( labeller_.getIcon( client ) );
        }
        return c;
    }
}
