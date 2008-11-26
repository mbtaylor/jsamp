package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
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

    private final boolean vertical_;
    private final int transSize_;
    private final List entryList_;
    private final int gap_;
    private final ListDataListener modelListener_;
    private CellRenderer renderer_;
    private ListModel model_;
    private Dimension minSize_;
    private Dimension maxSize_;
    private Dimension prefSize_;
    private static final ListModel EMPTY_LIST_MODEL = createEmptyListModel();

    /**
     * Constructor.
     *
     * @param   vertical  true for vertical run, false for horizontal
     * @param   transSize  the transverse (horizontal/vertical) size of the
     *          component in pixels
     */
    public IconBox( boolean vertical, int transSize ) {
        vertical_ = vertical;
        transSize_ = transSize;
        setOpaque( true );
        setBackground( Color.WHITE );
        setBorder( BorderFactory.createCompoundBorder(
                       BorderFactory.createLineBorder( Color.BLACK ),
                       BorderFactory.createEmptyBorder( 2, 2, 2, 2 ) ) );
        modelListener_ = new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( 0 <= i0 && i0 <= i1 && i1 <= entryList_.size() ) {
                    for ( int i = i0; i <= i1; i++ ) {
                        entryList_.set( i, createEntry( model_
                                                       .getElementAt( i ) ) );
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
                        entryList_.add( i, createEntry( model_
                                                       .getElementAt( i ) ) );
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
     * Refreshes the list-related state from scratch.
     */
    private void refreshState() {
        entryList_.clear();
        int count = model_.getSize();
        for ( int i = 0; i < count; i++ ) {
            entryList_.add( createEntry( model_.getElementAt( i ) ) );
        }
        repaint();
    }

    /**
     * Constructs an Entry object from an object contained in the ListModel,
     * using the currently installed renderer.
     */
    private Entry createEntry( Object value ) {
        return new Entry( renderer_.getIcon( value ),
                          renderer_.getToolTipText( value ) );
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
        int pTrans = vertical_ ? point.x : point.y;
        int pLeng = vertical_ ? point.y : point.x;
        Insets insets = getInsets();
        int lengLo = vertical_ ? insets.top : insets.left;
        if ( pTrans < ( vertical_ ? insets.left : insets.top ) ||
             pTrans > ( transSize_ + ( vertical_ ? insets.right
                                                 : insets.bottom ) ) ) {
            return -1;
        }
        else {
            pLeng -= vertical_ ? insets.top : insets.left;
            if ( pLeng < 0 ) {
                return -1;
            }
            int index = 0;
            for ( Iterator it = entryList_.iterator(); it.hasNext(); ) {
                Icon icon = ((Entry) it.next()).icon_;
                int leng = gap_ + ( vertical_ ? icon.getIconHeight()
                                              : icon.getIconWidth() );
                if ( pLeng < leng ) {
                    return index;
                }
                pLeng -= leng;
                index++;
            }
            return -1;
        }
    }

    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        if ( isOpaque() ) {
            Dimension size = getSize();
            Color color = g.getColor();
            g.setColor( getBackground() );
            g.fillRect( 0, 0, size.width, size.height );
            g.setColor( color );
        }
        Insets insets = getInsets();
        int x = insets.left;
        int y = insets.top;
        for ( Iterator it = entryList_.iterator(); it.hasNext(); ) {
            Icon icon = ((Entry) it.next()).icon_;
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

    public String getToolTipText( MouseEvent evt ) {
        int index = getIndexAt( evt.getPoint() );
        return index >= 0 ? ((Entry) entryList_.get( index )).tooltip_
                          : null;
    }

    /**
     * Defines how list model elements will be rendered as icons and tooltips.
     */
    public interface CellRenderer {

        /**
         * Returns the icon to be displayed for a given list model element.
         *
         * @param  value  list model element
         */
        Icon getIcon( Object value );

        /**
         * Returns the tooltip text to be used for a given list model element.
         * Null is OK.
         *
         * @param   value  list model element
         */
        String getToolTipText( Object value );
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
        public Icon getIcon( Object value ) {
            return value instanceof Icon
                 ? (Icon) value
                 : (Icon) this;
        }
        public String getToolTipText( Object value ) {
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
