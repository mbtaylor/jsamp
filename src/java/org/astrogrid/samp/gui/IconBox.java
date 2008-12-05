package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.ListModel;
import javax.swing.ToolTipManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * Component which displays the contents of a ListModel as icons.
 * Custom icon and tooltip generation are supported by use of a separate
 * renderer object.
 *
 * @author   Mark Taylor
 * @since    26 Nov 2008
 */
class IconBox extends JComponent {

    private final List entryList_;
    private final ListDataListener modelListener_;
    private final Color enabledBg_;
    private final Color disabledBg_;
    private boolean vertical_;
    private boolean trailing_;
    private boolean reversed_;
    private int transSize_;
    private int gap_;
    private CellRenderer renderer_;
    private ListModel model_;
    private Dimension minSize_;
    private Dimension maxSize_;
    private Dimension prefSize_;
    private static final ListModel EMPTY_LIST_MODEL = createEmptyListModel();

    /**
     * Constructor.
     *
     * @param   transSize  the transverse (horizontal/vertical) size 
     *          available for icons in pixels
     */
    public IconBox( int transSize ) {
        transSize_ = transSize;
        setOpaque( true );
        disabledBg_ = null;
        enabledBg_ = Color.WHITE;
        setBackground( enabledBg_ );
        modelListener_ = new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( 0 <= i0 && i0 <= i1 && i1 <= entryList_.size() ) {
                    for ( int i = i0; i <= i1; i++ ) {
                        entryList_.set( i, createEntry( i ) );
                    }
                    repaint();  // could be more efficient
                }
                else {
                   refreshState();
                }
            }
            public void intervalAdded( ListDataEvent evt ) {
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( 0 <= i0 && i0 <= i1 && i1 <= model_.getSize() ) {
                    for ( int i = i0; i <= i1; i++ ) {
                        entryList_.add( i, createEntry( i ) );
                    }
                    repaint();  // could be more efficient
                }
                else {
                    refreshState();
                }
            }
            public void intervalRemoved( ListDataEvent evt ) {
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( 0 <= i0 && i0 <= i1 && i1 <= entryList_.size() ) {
                    for ( int i = i1; i >= i0; i-- ) {
                        entryList_.remove( i );
                    }
                    repaint();  // could be more efficient
                }
                else {
                    refreshState();
                }
            }
        };
        entryList_ = new ArrayList();
        gap_ = 4;
        ToolTipManager.sharedInstance().registerComponent( this );
        setModel( EMPTY_LIST_MODEL );
        setRenderer( new DefaultRenderer() );
    }

    /**
     * Sets whether icons will be lined up in a horizontal or vertical line.
     *
     * @param   vertical  true for vertical run, false for horizontal
     */
    public void setVertical( boolean vertical ) {
        vertical_ = vertical;
        revalidate();
        repaint();
    }

    /**
     * Returns whether icons will be lined up horizontally or vertically.
     *
     * @return  false for hormizontal run, true for vertical
     */
    public boolean getVertical() {
        return vertical_;
    }

    /**
     * Sets the alignment of the icons in this component.
     *
     * @param  trailing  false for left/top, true for right/bottom alignment
     */
    public void setTrailing( boolean trailing ) {
        trailing_ = trailing;
        repaint();
    }

    /**
     * Returns the alignment of the icons in this component.
     *
     * @return   false for left/top, true for right/bottom alignment
     */
    public boolean getTrailing() {
        return trailing_;
    }

    /**
     * Sets the first-to-last ordering of the icons in this component.
     *
     * @param   reversed  false for increasing to right/bottom,
     *                    true for increasig to left/top
     */
    public void setReversed( boolean reversed ) {
        reversed_ = reversed;
        repaint();
    }

    /**
     * Returns the first-to-last ordering of the icons in this component.
     *
     * @return  false for increasing to right/bottom,
     *          true for increasig to left/top
     */
    public boolean getReversed() {
        return reversed_;
    }

    public void setEnabled( boolean enabled ) {
        super.setEnabled( enabled );
        setBackground( enabled ? enabledBg_ : disabledBg_ );
    }

    /**
     * Refreshes the list-related state from scratch.
     */
    private void refreshState() {
        entryList_.clear();
        int count = model_.getSize();
        for ( int i = 0; i < count; i++ ) {
            entryList_.add( createEntry( i ) );
        }
        repaint();
    }

    /**
     * Constructs an Entry object from an object contained in the ListModel,
     * using the currently installed renderer.
     *
     * @param  index   index of entry in list
     * @return  new entry
     */
    private Entry createEntry( int index ) {
        Object value = model_.getElementAt( index );
        return new Entry( renderer_.getIcon( this, value, index ),
                          renderer_.getToolTipText( this, value, index ) );
    }

    /**
     * Sets the list model for use with this component.
     * Objects will be rendered as icons by using the currently intalled
     * renderer.
     * 
     * @param  model  list model
     */
    public void setModel( ListModel model ) {
        if ( model_ != null ) {
            model_.removeListDataListener( modelListener_ );
        }
        model_ = model;
        if ( model_ != null ) {
            model_.addListDataListener( modelListener_ );
        }
        refreshState();
    }

    /**
     * Returns the list model used by this component.
     *
     * @return   list model
     */
    public ListModel getModel() {
        return model_;
    }

    /**
     * Sets the transverse dimension in pixels of this box.
     *
     * @param   transSize  pixel count across list run
     */
    public void setTransverseSize( int transSize ) {
        transSize_ = transSize;
        if ( transSize_ != transSize ) {
            repaint();
        }
    }

    /**
     * Returns the transverse dimension in pixels of this box.
     *
     * @return   pixel count across run
     */
    public int getTransverseSize() {
        return transSize_;
    }

    /**
     * Sets the object which is used to turn list model contents into the
     * icons and tooltips displayed by this component.
     *
     * @param  renderer  new renderer
     */
    public void setRenderer( CellRenderer renderer ) {
        renderer_ = renderer;
        refreshState();
    }

    /**
     * Returns the object which turns list model contents into display things.
     * The default value tries to cast to Icon and uses toString for tooltip.
     *
     * @return   current renderer  
     */
    public CellRenderer getRenderer() {
        return renderer_;
    }

    public void setPreferredSize( Dimension prefSize ) {
        prefSize_ = prefSize;
    }

    public Dimension getPreferredSize() {
        if ( prefSize_ == null ) {
            int leng = 0;
            for ( Iterator it = entryList_.iterator(); it.hasNext(); ) {
                Entry entry = (Entry) it.next();
                Icon icon = entry.icon_;
                leng += vertical_ ? icon.getIconHeight() : icon.getIconWidth();
                if ( it.hasNext() ) {
                    leng += gap_;
                }
            }
            Dimension size = vertical_ ? new Dimension( transSize_, leng )
                                       : new Dimension( leng, transSize_ );
            Insets insets = getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }
        else {
            return prefSize_;
        }
    }

    public void setMinimumSize( Dimension minSize ) {
        minSize_ = minSize;
    }

    public Dimension getMinimumSize() {
        if ( minSize_ == null ) {
            Dimension size = vertical_ ? new Dimension( transSize_, 0 )
                                       : new Dimension( 0, transSize_ );
            Insets insets = getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }
        else {
            return minSize_;
        }
    }

    public void setMaximumSize( Dimension maxSize ) {
        maxSize_ = maxSize;
    }

    public Dimension getMaximumSize() {
        if ( maxSize_ == null ) {
            Dimension size = new Dimension( transSize_, transSize_ );
            Insets insets = getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            if ( vertical_ ) {
                size.height = Integer.MAX_VALUE;
            }
            else {
                size.width = Integer.MAX_VALUE;
            }
            return size;
        }
        else {
            return maxSize_;
        }
    }

    /**
     * Returns the index of the list model element whose icon is displayed
     * at a given point in this component.
     *
     * @param  point  point to interrogate
     * @return   list model index, or -1 if not found
     */
    public int getIndexAt( Point point ) {
        Dimension size = getSize();
        Insets insets = getInsets();

     // The following should reject the request if it's outside this components
     // bounds.  However, it seems that sometimes (always??) getSize() reports
     // zero size.  I don't understand why, and I'm surprised the rest of the
     // functionality here works under these circumstances; but it does.
     // Leave it like this for now.
     // if ( point.x < insets.left || point.x > size.width - insets.right ||
     //      point.y < insets.top || point.y > size.height - insets.bottom ) {
     //     return -1;
     // }
        List entryList = entryList_;
        if ( reversed_ ) {
            entryList = new ArrayList( entryList );
            Collections.reverse( entryList );
        }
        int pLeng = trailing_
                  ? ( vertical_ ? size.height - insets.bottom - point.y
                                : size.width - insets.right - point.x )
                  : ( vertical_ ? point.y - insets.top
                                : point.x - insets.left );
        int index = 0;
        for ( Iterator it = entryList.iterator(); it.hasNext(); ) {
            Icon icon = ((Entry) it.next()).icon_;
            int leng = gap_ + ( vertical_ ? icon.getIconHeight()
                                          : icon.getIconWidth() );
            if ( pLeng < leng ) {
                if ( index < entryList.size() ) {
                    return index;
                }
                else {
                    assert false;
                    return -1;
                }
            }
            pLeng -= leng;
            index++;
        }
        return -1;
    }

    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        Dimension size = getSize();
        if ( isOpaque() ) {
            Color color = g.getColor();
            g.setColor( getBackground() );
            g.fillRect( 0, 0, size.width, size.height );
            g.setColor( color );
        }
        Insets insets = getInsets();
        List entryList = entryList_;
        if ( reversed_ ) {
            entryList = new ArrayList( entryList );
            Collections.reverse( entryList );
        }
        if ( entryList.isEmpty() ) {
            return;
        }
        int x = vertical_
              ? insets.left
              : ( trailing_ ? size.width - insets.right
                                         - ((Entry) entryList.get( 0 ))
                                          .icon_.getIconWidth()
                            : insets.left );
        int y = vertical_
              ? ( trailing_ ? size.height - insets.bottom
                                          - ((Entry) entryList.get( 0 ))
                                           .icon_.getIconHeight()
                            : insets.top )
              : insets.top;
        for ( Iterator it = entryList.iterator(); it.hasNext(); ) {
            Icon icon = ((Entry) it.next()).icon_;
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            if ( g.hitClip( x, y, width, height ) ) {
                icon.paintIcon( this, g, x, y );
            }
            if ( vertical_ ) {
                y += ( trailing_ ? -1 : +1 ) * ( height + gap_ );
            }
            else {
                x += ( trailing_ ? -1 : +1 ) * ( width + gap_ );
            }
        }
    }

    public String getToolTipText( MouseEvent evt ) {
        int index = getIndexAt( evt.getPoint() );
        return index >= 0 ? ((Entry) entryList_.get( index )).tooltip_
                          : null;
    }

    /**
     * Defines how list model elements will be rendered as icons and tooltips.
     */
    interface CellRenderer {

        /**
         * Returns the icon to be displayed for a given list model element.
         *
         * @param  iconBox  component using this renderer
         * @param  value  list model element
         * @param  index  index in the entry list being rendered
         * @return  icon to paint
         */
        Icon getIcon( IconBox iconBox, Object value, int index );

        /**
         * Returns the tooltip text to be used for a given list model element.
         * Null is OK.
         *
         * @param  iconBox  component using this renderer
         * @param   value  list model element
         * @param  index  index in the entry list being rendered
         * @return   tooltip for value
         */
        String getToolTipText( IconBox iconBox, Object value, int index );
    }

    /**
     * Convenience struct-type class which aggregates an icon and a tooltip.
     */
    private static class Entry {
        final Icon icon_;
        final String tooltip_;

        /**
         * Constructor.
         *
         * @param  icon  icon
         * @param  tooltip  tooltip
         */
        Entry( Icon icon, String tooltip ) {
            icon_ = icon;
            tooltip_ = tooltip;
        }
    }

    /**
     * Constructs an immutable list model with no content.
     *
     * @return  dummy list model
     */
    private static ListModel createEmptyListModel() {
        return new AbstractListModel() {
            public int getSize() {
                return 0;
            }
            public Object getElementAt( int index ) {
                return null;
            }
        };
    }

    /**
     * Default renderer.
     */
    private class DefaultRenderer implements CellRenderer, Icon {
        public Icon getIcon( IconBox iconBox, Object value, int index ) {
            return value instanceof Icon
                 ? (Icon) value
                 : (Icon) this;
        }
        public String getToolTipText( IconBox iconBox, Object value,
                                      int index ) {
            return value == null
                 ? null
                 : value.toString();
        }
        public int getIconWidth() {
            return transSize_;
        }
        public int getIconHeight() {
            return transSize_;
        }
        public void paintIcon( Component c, Graphics g, int x, int y ) {
            g.drawOval( x, y, transSize_, transSize_ );
        }
    }
}
