package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;
import org.astrogrid.samp.Response;

/**
 * CellRenderer for transmission objects.
 *
 * @author   Mark Taylor
 * @since    27 Nov 2008
 */
class TransmissionCellRenderer implements IconBox.CellRenderer {

    private static final Color SUCCESS_COLOR = new Color( 0x00c000 );
    private static final Color FAILURE_COLOR = new Color( 0xc00000 );
    private static final Color NOTIFIED_COLOR = new Color( 0x808080 );

    public Icon getIcon( IconBox iconBox, Object value, int index ) {
        Insets insets = iconBox.getInsets();
        final int size = iconBox.getTransverseSize();
        if ( value instanceof Transmission ) {
            final Transmission trans = (Transmission) value;
            final int width = (int) Math.floor( 0.866 * size );
            return new Icon() {
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                    int xlo = x;
                    int xhi = x + size;
                    int[] xs = new int[] { x, x + width, x, };
                    int[] ys = new int[] { y, y + size / 2, y + size, };
                    Color gcolor = g.getColor();
                    g.setColor( getColor( trans ) );
                    if ( trans.isDone() ) {
                        g.drawPolygon( xs, ys, 3 );
                    }
                    else {
                        g.fillPolygon( xs, ys, 3 );
                    }
                    g.setColor( gcolor );
                }
                public int getIconWidth() {
                    return width;
                }
                public int getIconHeight() {
                    return size;
                }
            };
        }
        else {
            return new Icon() {
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                    int s = size - 3 + ( size % 2 );
                    g.drawOval( x + 1, y + 1, s, s );
                }
                public int getIconWidth() {
                    return size;
                }
                public int getIconHeight() {
                    return size;
                }
            };
        }
    }

    public String getToolTipText( IconBox iconBox, Object value, int index ) {
        if ( value instanceof Transmission ) {
            return ((Transmission) value).getMessage().getMType();
        }
        else {
            return null;
        }
    }

    /**
     * Returns the colour used to render a transmission, dependent on its state.
     *
     * @param  trans  transmission
     * @return   colour
     */
    public Color getColor( Transmission trans ) {
        if ( trans.isDone() ) {
            Response response = trans.getResponse();
            if ( response == null ) {
                return NOTIFIED_COLOR;
            }
            else if ( response.isOK() ) {
                return SUCCESS_COLOR;
            }
            else {
                return FAILURE_COLOR;
            }
        }
        else {
            return Color.BLACK;
        }
    }
}
