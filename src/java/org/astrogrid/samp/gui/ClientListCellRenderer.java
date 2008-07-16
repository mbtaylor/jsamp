package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.RegInfo;

/**
 * List Cell Renderer for use with {@link org.astrogrid.samp.Client} objects.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class ClientListCellRenderer extends DefaultListCellRenderer {

    private final ClientLabeller labeller_;
    private Font[] labelFonts_;

    /**
     * Constructor.
     *
     * @param  listModel  list model whose elements we will be rendering
     * @param  regInfo    registration information for hub connection to
     *                    which clients apply (may be null)
     */
    public ClientListCellRenderer( ListModel listModel, RegInfo regInfo ) {
        labeller_ = new ClientLabeller( listModel, regInfo );
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
            jl.setIcon( sizeIcon( labeller_.getIcon( client ), size ) );
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
     * Return an icon based on an existing one, but drawn to an exact size.
     *
     * @param  icon  original icon, or null for blank
     * @param  size  number of horizontal and vertical pixels in output
     * @return  resized version of <code>icon</code>
     */
    private static Icon sizeIcon( Icon icon, final int size ) {
        if ( icon == null ) {
            return new Icon() {
                public int getIconWidth() {
                    return size;
                }
                public int getIconHeight() {
                    return size;
                }
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                }
            };
        }
        else if ( icon.getIconWidth() == size &&
                  icon.getIconHeight() == size ) {
            return icon;
        }
        else {
            return new SizedIcon( icon, size );
        }
    }

    /**
     * Icon implementation which looks like an existing one, but is resized
     * down if necessary.
     */
    private static class SizedIcon implements Icon {
        private final Icon icon_;
        private final int size_;
        private final double factor_;

        /**
         * Constructor.
         *
         * @param   icon  original icon
         * @param   size  number of horizontal and vertical pixels in this icon
         */
        public SizedIcon( Icon icon, int size ) {
            icon_ = icon;
            size_ = size;
            factor_ =
                Math.min( 1.0,
                          Math.min( size / (double) icon.getIconWidth(),
                                    size / (double) icon.getIconHeight() ) );
        }

        public int getIconWidth() {
            return size_;
        }

        public int getIconHeight() {
            return size_;
        }

        public void paintIcon( Component c, Graphics g, int x, int y ) {
            int iw = icon_.getIconWidth();
            int ih = icon_.getIconHeight();
            if ( factor_ == 1.0 ) {
                icon_.paintIcon( c, g, ( size_ - iw ) / 2, ( size_ - ih ) / 2 );
            }
            else {
                Graphics2D g2 = (Graphics2D) g;
                AffineTransform trans = g2.getTransform();
                g2.translate( ( size_ - iw * factor_ ) / 2,
                              ( size_ - ih * factor_ ) / 2 );
                g2.scale( factor_, factor_ );
                icon_.paintIcon( c, g2, x, y );
                g2.setTransform( trans );
            }
        }
    }
}
