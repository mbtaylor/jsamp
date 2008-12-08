package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

/**
 * CellRenderer for transmission objects.
 *
 * @author   Mark Taylor
 * @since    27 Nov 2008
 */
class TransmissionCellRenderer implements IconBox.CellRenderer {

    public Icon getIcon( IconBox iconBox, Object value, int index ) {
        final int size = iconBox.getTransverseSize();
        if ( value instanceof Transmission ) {
            return ((Transmission) value).getStatus().getIcon( size );
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
}
