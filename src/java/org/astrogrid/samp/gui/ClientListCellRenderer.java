package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Font;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.RegInfo;

public class ClientListCellRenderer extends DefaultListCellRenderer {

    private final ClientLabeller labeller_;
    private final RegInfo regInfo_;
    private Font[] labelFonts_;

    public ClientListCellRenderer( ListModel listModel, RegInfo regInfo ) {
        labeller_ = new ClientLabeller();
        labeller_.setClientListModel( listModel );
        regInfo_ = regInfo;
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( c instanceof JLabel && value instanceof Client ) {
            JLabel jl = (JLabel) c;
            Client client = (Client) value;
            String id = client.getId();
            String label = labeller_.getLabel( client );
            String text = label == null ? id
                                        : label;
            if ( regInfo_ != null ) {
                if ( id.equals( regInfo_.getHubId() ) ) {
                    text += " (hub)";
                }
                else if ( id.equals( regInfo_.getSelfId() ) ) {
                    text += " (self)";
                }
            }
            jl.setText( text );
            jl.setFont( getLabelFont( label == null ) );
            jl.setIcon( labeller_.getIcon( client ) );
        }
        return c;
    }

    private Font getLabelFont( boolean isAlias ) {
        if ( labelFonts_ == null ) {
            Font normalFont = getFont();
            Font aliasFont = getFont().deriveFont( Font.ITALIC );
            labelFonts_ = new Font[] { normalFont, aliasFont };
        }
        return labelFonts_[ isAlias ? 1 : 0 ];
    }
}
