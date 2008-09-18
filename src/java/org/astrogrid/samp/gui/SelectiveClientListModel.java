package org.astrogrid.samp.gui;

import java.util.Arrays;
import javax.swing.AbstractListModel;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;

/**
 * ListModel implementation which sits on top of an existing ListModel
 * containing {@link org.astrogrid.samp.Client}s, but only includes a
 * subset of its elements.
 *
 * <p>Concrete subclasses must
 * <ol>
 * <li>implement the {@link #isIncluded} method to determine which clients
 *     from the base list appear in this one</li>
 * <li>call {@link #init} before the class is used 
 *     (for instance in their constructor)</li>
 * <ol>
 *
 * @author   Mark Taylor
 * @since    1 Sep 2008
 */
public abstract class SelectiveClientListModel extends AbstractListModel {

    private final ListModel baseModel_;
    private final ListDataListener listDataListener_;
    private int[] map_;

    /**
     * Constructor.
     *
     * @param   clientListModel   base ListModel containing 
     *                            {@link org.astrogrid.samp.Client} objects
     */
    public SelectiveClientListModel( ListModel clientListModel ) {
        baseModel_ = clientListModel;

        // Somewhat haphazard implementation.  The ListDataListener interface
        // is not constructed (or documented) so as to make it easy to 
        // fire the right events.  Some efficiency measures are taken here,
        // but it would be possible to do more.
        listDataListener_ = new ListDataListener() {
            public void contentsChanged( ListDataEvent evt ) {
                int[] oldMap = map_;
                map_ = calculateMap();
                if ( Arrays.equals( oldMap, map_ ) &&
                     evt.getIndex0() == evt.getIndex1() &&
                     evt.getIndex0() >= 0 ) {
                    int index = evt.getIndex0();
                    for ( int i = 0; i < map_.length; i++ ) {
                        if ( map_[ i ] == index ) {
                            fireContentsChanged( this, index, index );
                        }
                    }
                }
                else {
                    fireContentsChanged( this, -1, -1 );
                }
            }
            public void intervalAdded( ListDataEvent evt ) {
                int[] oldMap = map_;
                map_ = calculateMap();
                if ( ! Arrays.equals( oldMap, map_ ) ) {
                    int leng = Math.min( map_.length, oldMap.length );
                    int index0 = -1;
                    for ( int i = 0; i < leng; i++ ) {
                        if ( oldMap[ i ] != map_[ i ] ) {
                            index0 = i;
                            break;
                        }
                    }
                    int index1 = -1;
                    for ( int i = 0; i < leng; i++ ) {
                        if ( oldMap[ oldMap.length - 1 - i ] !=
                             map_[ map_.length - 1 - i ] ) {
                            index1 = map_.length - 1 - i;
                            break;
                        }
                    }
                    if ( index0 >= 0 && index1 >= 0 ) {
                        fireIntervalAdded( this, index0, index1 );
                    }
                    else {
                        fireContentsChanged( this, -1, -1 );
                    }
                }
            }
            public void intervalRemoved( ListDataEvent evt ) {
                int[] oldMap = map_;
                map_ = calculateMap();
                if ( ! Arrays.equals( oldMap, map_ ) ) {
                    fireContentsChanged( this, -1, -1 );
                }
            }
        };
    }

    /**
     * Implement this method to determine which clients are included in 
     * this list.
     *
     * @param   client   client for consideration
     * @return   true  iff client is to be included in this list
     */
    protected abstract boolean isIncluded( Client client );

    /**
     * Must be called by subclass prior to use.
     */
    protected void init() {
        refresh();
        baseModel_.addListDataListener( listDataListener_ );
    }

    /**
     * Recalculates the inclusions.  This should be called if the return
     * value from {@link #isIncluded} might have changed for some of the
     * elements.
     */
    protected void refresh() {
        map_ = calculateMap();
    }

    public int getSize() {
        return map_.length;
    }

    public Object getElementAt( int index ) {
        return baseModel_.getElementAt( map_[ index ] );
    }

    /**
     * Releases any resources associated with this transmitter.
     */
    public void dispose() {
        baseModel_.removeListDataListener( listDataListener_ );
    }

    /**
     * Recalculates the this list -> base list lookup table.
     *
     * @return   array whose indices represent elements of this list, and
     *           values represent elements of the base list
     */
    private int[] calculateMap() {
        int nc = baseModel_.getSize();
        int[] map = new int[ nc ];
        int ij = 0;
        for ( int ic = 0; ic < nc; ic++ ) {
            Client client = (Client) baseModel_.getElementAt( ic );
            if ( isIncluded( client ) ) {
                map[ ij++ ] = ic;
            }
        }
        int[] map1 = new int[ ij ];
        System.arraycopy( map, 0, map1, 0, ij );
        return map1;
    }
}
