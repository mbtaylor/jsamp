package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;

/**
 * CellRenderer for transmission objects.
 *
 * @author   Mark Taylor
 * @since    27 Nov 2008
 */
class TransmissionCellRenderer implements IconBox.CellRenderer {

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
                    g.fillPolygon( xs, ys, 3 );
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
                    g.drawOval( x, y, size, size );
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
}
