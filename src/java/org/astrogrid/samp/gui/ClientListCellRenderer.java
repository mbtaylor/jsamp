package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;

/**
 * List Cell Renderer for use with {@link org.astrogrid.samp.Client} objects.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
class ClientListCellRenderer extends DefaultListCellRenderer {

    private Font[] labelFonts_;
    private IconStore iconStore_;

    /**
     * Constructor.
     */
    public ClientListCellRenderer() {
        iconStore_ = new IconStore( -1, IconStore.createEmptyIcon( 16 ) );
    }

    /**
     * Attempts to return a human-readable text label for the given client.
     *
     * @param   client  to find label for
     * @return  human-readable label for client if available; if nothing
     *          better than the public ID can be found, null is returned
     */
    protected String getLabel( Client client ) {
        Metadata meta = client.getMetadata();
        return meta != null ? meta.getName()
                            : null;
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( c instanceof JLabel && value instanceof Client ) {
            JLabel jl = (JLabel) c;
            Client client = (Client) value;
            String label = getLabel( client );
            String text = label == null ? client.getId() : label;
            Font font = getLabelFont( label == null );
            int size;
            try {
                size = (int)
                    Math.ceil( font.getMaxCharBounds( ((Graphics2D)
                                                       list.getGraphics())
                                                     .getFontRenderContext() )
                                   .getHeight() );
            }
            catch ( NullPointerException e ) {
                size = 16;
            }
            jl.setText( text );
            jl.setFont( font );
            jl.setIcon( IconStore
                       .sizeIcon( iconStore_.getIcon( client ), size ) );
        }
        return c;
    }

    /**
     * Returns the font used by this label, or a variant.
     *
     * @param   special   true if the font is to look a bit different
     * @return  font
     */
    private Font getLabelFont( boolean special ) {
        if ( labelFonts_ == null ) {
            Font normalFont = getFont().deriveFont( Font.BOLD );
            Font aliasFont = getFont().deriveFont( Font.PLAIN );
            labelFonts_ = new Font[] { normalFont, aliasFont };
        }
        return labelFonts_[ special ? 1 : 0 ];
    }
}
