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

    private Font getLabelFont( boolean isAlias ) {
        if ( labelFonts_ == null ) {
            Font normalFont = getFont();
            Font aliasFont = getFont().deriveFont( Font.ITALIC );
            labelFonts_ = new Font[] { normalFont, aliasFont };
        }
        return labelFonts_[ isAlias ? 1 : 0 ];
    }

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

    private static class SizedIcon implements Icon {
        private final Icon icon_;
        private final int size_;
        private final double factor_;

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
