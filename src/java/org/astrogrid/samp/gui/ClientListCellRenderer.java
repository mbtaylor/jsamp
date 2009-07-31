package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
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
    private final Map addHints_;

    /**
     * Constructor.
     */
    public ClientListCellRenderer() {
        iconStore_ = new IconStore( IconStore.createEmptyIcon( 16 ) );
        addHints_ = new HashMap();
        addHints_.put( RenderingHints.KEY_RENDERING,
                       RenderingHints.VALUE_RENDER_QUALITY );
        addHints_.put( RenderingHints.KEY_INTERPOLATION,
                       RenderingHints.VALUE_INTERPOLATION_BICUBIC );
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

    protected void paintComponent( Graphics g ) {

        // Improve the rendering as much as possible, since we are typically
        // rendering some very small graphics that can look ugly if 
        // the resampling puts pixels out of place.
        Graphics2D g2 = (Graphics2D) g;
        RenderingHints oldHints = g2.getRenderingHints();
        g2.addRenderingHints( addHints_ );
        super.paintComponent( g );
        g2.setRenderingHints( oldHints );
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
            jl.setIcon( reshapeIcon( iconStore_.getIcon( client ), size ) );
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

    /**
     * Modifies an icon so that it has a fixed shape and positioning.
     *
     * @param    icon   input icon
     * @param    height  fixed icon height
     * @return   reshaped icon
     */
    static Icon reshapeIcon( Icon icon, final int height ) {
        double aspect = 2.0;
        final int width = (int) Math.ceil( aspect * height );
        final Icon sIcon = IconStore.scaleIcon( icon, height, aspect, true );
        final int xoff = ( width - sIcon.getIconWidth() ) / 2;
        return new Icon() {
            public int getIconWidth() {
                return width;
            }
            public int getIconHeight() {
                return height;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                sIcon.paintIcon( c, g, x + xoff, y );
            }
        };
    }
}
