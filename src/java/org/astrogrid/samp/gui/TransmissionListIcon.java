package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import javax.swing.Icon;
import javax.swing.ListModel;

/**
 * Icon which paints a graphical representation of a list of Transmissions.
 *
 * @author   Mark Taylor
 * @since    21 Nov 2008
 */
public class TransmissionListIcon implements Icon {

    private final ListModel rxModel_;
    private final ListModel txModel_;
    private final int size_;
    private final int transIconWidth_;
    private final Icon targetIcon_;

    /**
     * Constructor.
     *
     * @param  rxModel  list of messages received;
     *                  all elements must be {@link Transmission} objects
     * @param  txModel  list of messages sent;
     *                  all elements must be {@link Transmission} objects
     * @param  size     height of icon in pixels; this also scales the width
     */
    public TransmissionListIcon( ListModel rxModel, ListModel txModel,
                                 int size ) {
        rxModel_ = rxModel;
        txModel_ = txModel;
        size_ = size;
        transIconWidth_ = (int) Math.floor( size_ * 0.866 );  // equilateral
        final boolean hasTx = txModel_ != null;
        final boolean hasRx = rxModel_ != null;
        targetIcon_ = new Icon() {
            public int getIconWidth() {
                return size_;
            }
            public int getIconHeight() {
                return size_;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                g.drawOval( x + 1, y + 1, size_ - 2, size_ - 2 );
            }
        };
    }

    /**
     * Returns the transmission (if any) which is painted at a given point.
     * 
     * @param   point   screen point relative to the origin of this icon
     * @return  transmission painted at <code>point</code> or null if there
     *          isn't one
     */
    public Transmission getTransmissionAt( Point point ) {
        int x = point.x;
        int y = point.y;
        if ( x < 0 || x > getIconHeight() || y < 0 || y > getIconWidth() ) {
            return null;
        }
        int x0 = 0;

        int rxWidth = rxModel_.getSize() * transIconWidth_;
        if ( x - x0 < rxWidth ) {
            int ir = ( x - x0 ) / transIconWidth_;
            return (Transmission) rxModel_.getElementAt( ir );
        }
        x0 += rxWidth;

        int targetWidth = targetIcon_.getIconWidth();
        if ( x - x0 < targetWidth ) {
            return null;
        }
        x0 += targetWidth;

        int txWidth = txModel_.getSize() * transIconWidth_;
        if ( x - x0 < txWidth ) {
            int it = ( x - x0 ) / transIconWidth_;
            return (Transmission) txModel_.getElementAt( it );
        }
        x0 += txWidth;

        assert x > x0;
        return null;
    }

    public int getIconWidth() {
        return ( rxModel_ != null ? rxModel_.getSize() * transIconWidth_ : 0 )
             + targetIcon_.getIconWidth()
             + ( txModel_ != null ? txModel_.getSize() * transIconWidth_ : 0 );
    }

    public int getIconHeight() {
        return size_;
    }

    public void paintIcon( Component c, Graphics g, int x, int y ) {
        if ( rxModel_ != null ) {
            for ( int i = 0; i < rxModel_.getSize(); i++ ) {
                Transmission trans = (Transmission) rxModel_.getElementAt( i );
                Icon transIcon = getTransIcon( trans, false );
                transIcon.paintIcon( c, g, x, y );
                x += transIcon.getIconWidth();
            }
        }
        targetIcon_.paintIcon( c, g, x, y );
        x += targetIcon_.getIconWidth();
        if ( txModel_ != null ) {
            for ( int i = 0; i < txModel_.getSize(); i++ ) {
                Transmission trans = (Transmission) txModel_.getElementAt( i );
                Icon transIcon = getTransIcon( trans, true );
                transIcon.paintIcon( c, g, x, y );
                x += transIcon.getIconWidth();
            }
        }
    }

    /**
     * Returns an icon which can paint a particular transmission.
     *
     * @param   trans   transmission
     * @param   isTx  true if <code>trans</code> represents a send,
     *                false if it represents a receive
     */
    private Icon getTransIcon( Transmission trans, final boolean isTx ) {
        return new Icon() {
            public int getIconHeight() {
                return size_;
            }
            public int getIconWidth() {
                return transIconWidth_;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                int xlo = x + 1;
                int xhi = x + transIconWidth_ - 1;
                int[] xs = isTx ? new int[] { xhi, xlo, xhi, }
                                : new int[] { xlo, xhi, xlo, };
                int[] ys = new int[] { y, y + size_ / 2, y + size_ - 1 };
                g.fillPolygon( xs, ys, 3 );
            }
        };
    }
}
