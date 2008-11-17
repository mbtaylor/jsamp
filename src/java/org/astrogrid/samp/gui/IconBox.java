package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.ListModel;
import javax.swing.ToolTipManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * Component which displays the contents of a list with {@link #Entry} elements.
 * Each icon is considered to have the same dimensions.
 *
 * @author   Mark Taylor
 * @since    17 Nov
 */
class IconBox extends JComponent {

    private final boolean vertical_;
    private ListModel entryListModel_;
    private final int iconSize_;
    private final int border_;
    private final int gap_;
    private Dimension prefSize_;
    private Dimension minSize_;
    private final ListDataListener modelListener_;
    private static final ListModel EMPTY_LIST_MODEL = createEmptyListModel();

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
        entryListModel_ = EMPTY_LIST_MODEL;
        border_ = 2;
        gap_ = 4;
        modelListener_ = new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                repaint();
            }
            public void intervalAdded( ListDataEvent evt ) {
                repaint();
            }
            public void intervalRemoved( ListDataEvent evt ) {
                repaint();
            }
        };
        ToolTipManager.sharedInstance().registerComponent( this );
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
     * Sets the list model of entries displayed by this box.
     *
     * @param  entryListModel  ListModel in which elements are {@link #Entry}s
     */
    public void setModel( ListModel entryListModel ) {
        if ( entryListModel_ != null ) {
            entryListModel_.removeListDataListener( modelListener_ );
        }
        entryListModel_ = entryListModel;
        if ( entryListModel_ != null ) {
            entryListModel_.addListDataListener( modelListener_ );
        }
    }

    /**
     * Returns the entry under a given point on the screen.
     *
     * @point   position
     * @return   entry at point, or null
     */
    public Entry getEntryAt( Point point ) {
        int index = ( ( vertical_ ? ( point.y - getY() )
                                  : ( point.x - getX() ) ) - border_ )
                  / ( iconSize_ + gap_ * 2 );
        return index >= 0 && index < entryListModel_.getSize()
             ? (Entry) entryListModel_.getElementAt( index )
             : null;
    }

    public void setPreferredSize( Dimension prefSize ) {
        prefSize_ = prefSize;
    }

    public Dimension getPreferredSize() {
        return prefSize_ == null ? getSizeForSlots( entryListModel_.getSize() )
                                 : prefSize_;
    }

    public void setMinimumSize( Dimension minSize ) {
        minSize_ = minSize;
    }

    public Dimension getMinimumSize() {
        return minSize_ == null ? getSizeForSlots( 2 )
                                : prefSize_;
    }

    public String getToolTipText( MouseEvent evt ) {
        Entry entry = getEntryAt( evt.getPoint() );
        return entry == null ? null
                             : entry.getToolTipText();
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
        int nEntry = entryListModel_.getSize();
        for ( int ie = 0; ie < nEntry; ie++ ) {
            Entry entry = (Entry) entryListModel_.getElementAt( ie );
            Icon icon = IconStore.sizeIcon( entry.getIcon(), iconSize_ );
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            if ( g.hitClip( x, y, width, height ) ) {
                icon.paintIcon( this, g, x, y );
            }
            if ( vertical_ ) {
                y += height + gap_;
            }
            else {
                x += width + gap_;
            }
        }
    }

    /**
     * Creates an empty and unmodifiable list model.
     *
     * @return   empty list model
     */
    private static ListModel createEmptyListModel() {
        return new AbstractListModel() {
            public int getSize() {
                return 0;
            }
            public Object getElementAt( int index ) {
                throw new IllegalArgumentException( "No data" );
            }
        };
    }

    /**
     * Interface for items stored in this box's list.
     */
    public interface Entry {

        /**
         * Returns the icon for this entry.
         *
         * @return  icon
         */
        public Icon getIcon();

        /**
         * Returns the tooltip text for this entry.
         *
         * @return  tooltip text
         */
        public String getToolTipText();
    }
}
