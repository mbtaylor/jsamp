package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Component which displays the contents of a list with Icon elements.
 * Each icon is considered to have the same dimensions.
 *
 * <p>Unlike for instance a <code>JList</code>,
 * this component does not listen for changes to its model.
 * It is necessary to call a <code>repaint</code> if the icon list may have
 * changed.
 *
 * @author   Mark Taylor
 * @since    17 Nov
 */
class IconBox extends JComponent {

    private final boolean vertical_;
    private Collection iconList_;
    private final int iconSize_;
    private final int border_;
    private final int gap_;
    private Dimension prefSize_;
    private Dimension minSize_;

    /**
     * Constructor.
     *
     * @param   vertical   true for vertical box, false for horizontal
     * @param   iconSize   dimension in pixels of (square) icons
     */
    public IconBox( boolean vertical, int iconSize ) {
        setOpaque( true );
        setBackground( Color.WHITE );
        setBorder( BorderFactory.createLineBorder( Color.BLACK ) );
        vertical_ = vertical;
        iconSize_ = iconSize;
        iconList_ = new ArrayList();
        border_ = 2;
        gap_ = 4;
    }

    /**
     * Returns the shape that this component must have to display a given
     * number of icons.  The result may be used as the input to 
     * <code>get{Minimum,Preferred,Maximum}Size</code> JComponent methods.
     *
     * @param   nIcon  number of icons
     * @return  dimensions of this box when containing <code>nIcon</code> icons
     */
    public Dimension getSizeForSlots( int nIcon ) {
        Insets insets = getInsets();
        int length = nIcon * iconSize_ + ( nIcon - 1 ) * gap_ + 2 * border_;
        int trans = iconSize_ + 2 * border_;
        Dimension size = vertical_ ? new Dimension( trans, length )
                                   : new Dimension( length, trans );
        size.width += insets.left + insets.right;
        size.height += insets.top + insets.bottom;
        return size;
    }

    /**
     * Sets the list of icons.
     */
    public void setIcons( Collection iconList ) {
        iconList_ = iconList;
    }

    public void setPreferredSize( Dimension prefSize ) {
        prefSize_ = prefSize;
    }

    public Dimension getPreferredSize() {
        return prefSize_ == null ? getSizeForSlots( iconList_.size() )
                                 : prefSize_;
    }

    public void setMinimumSize( Dimension minSize ) {
        minSize_ = minSize;
    }

    public Dimension getMinimumSize() {
        return minSize_ == null ? getSizeForSlots( 2 )
                                : prefSize_;
    }

    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        Color color = g.getColor();
        Rectangle bounds = getBounds();
        Insets insets = getInsets();
        if ( isOpaque() ) {
            g.setColor( getBackground() );
            g.fillRect( bounds.x + insets.left, bounds.y + insets.top,
                        bounds.width - insets.left - insets.right,
                        bounds.height - insets.top - insets.bottom );
        }
        g.setColor( color );
        int x = insets.left + border_;
        int y = insets.top + border_;
        for ( Iterator it = iconList_.iterator(); it.hasNext(); ) {
            Icon icon = IconStore.sizeIcon( (Icon) it.next(), iconSize_ );
            icon.paintIcon( this, g, x, y );
            if ( vertical_ ) {
                y += icon.getIconHeight() + gap_;
            }
            else {
                x += icon.getIconWidth() + gap_;
            }
        }
    }
}
