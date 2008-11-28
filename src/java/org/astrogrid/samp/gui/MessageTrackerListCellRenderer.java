package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.SampUtils;

/**
 * ListCellRenderer which draws a representation of incoming and outgoing
 * messages alongside the default representation.
 *
 * @author   Mark Taylor
 * @since    26 Nov 2008
 */
class MessageTrackerListCellRenderer extends ClientListCellRenderer {

    private final ClientTransmissionHolder transHolder_;
    private final int msgGap_;
    private final IconBox iconBox_;
    private final IconListModel iconListModel_;
    private final Object separator_;

    /**
     * Constructor.
     *
     * @param   transHolder  obtains list models containing sent/received
     *          messages
     */
    public MessageTrackerListCellRenderer(
            ClientTransmissionHolder transHolder ) {
        transHolder_ = transHolder;
        iconListModel_ = new IconListModel();
        msgGap_ = 10;
        separator_ = new Object();
        iconBox_ = new IconBox( 16 );
        iconBox_.setOpaque( false );
        iconBox_.setBorder( BorderFactory.createEmptyBorder( 1, 1, 1, 1 ) );
        iconBox_.setModel( iconListModel_ );
        iconBox_.setRenderer( new TransmissionCellRenderer() {
            public String getToolTipText( IconBox iconBox, Object value,
                                          int index ) {
                if ( value instanceof Transmission ) {
                    Transmission trans = (Transmission) value;
                    String mtype = trans.getMessage().getMType();
                    Client client = iconListModel_.client_;
                    if ( client == trans.getSender() ) {
                        return mtype + " -> "
                             + SampUtils.toString( trans.getReceiver() );
                    }
                    else if ( client == trans.getReceiver() ) {
                        return mtype + " <- "
                             + SampUtils.toString( trans.getSender() );
                    }
                    else {
                        assert false;
                        return null;
                    }
                }
                else {
                    return null;
                }
            }
        } );
    }

    /**
     * Sets the cell renderer for transmission objects.
     *
     * @param  transRend  renderer
     */
    public void setTransmissionCellRenderer( IconBox.CellRenderer transRend ) {
        iconBox_.setRenderer( transRend );
    }

    /**
     * Returns the cell renderer for transmission objects.
     *
     * @return  renderer
     */
    public IconBox.CellRenderer getTransmissionCellRenderer() {
        return iconBox_.getRenderer();
    }

    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        if ( iconListModel_.client_ != null ) {
            Point p = getIconBoxPosition();
            Dimension boxSize = iconBox_.getPreferredSize();
            if ( g.hitClip( p.x, p.y, boxSize.width, boxSize.height ) ) {
                g.translate( p.x, p.y );
                iconBox_.paintComponent( g );
                g.translate( -p.x, -p.y );
            }
        }
    }

    public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        if ( iconListModel_.client_ != null ) {
            Dimension boxSize = iconBox_.getPreferredSize();
            prefSize.width += iconBox_.getPreferredSize().width + msgGap_;
        }
        return prefSize;
    }

    public String getToolTipText( MouseEvent evt ) {
        Point boxPos = getIconBoxPosition();
        evt.translatePoint( -boxPos.x, -boxPos.y );
        return iconBox_.getToolTipText( evt );
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( value instanceof Client ) {
            iconListModel_.setClient( (Client) value );
            int size = c.getPreferredSize().height;
            if ( c instanceof JComponent ) {
                Insets cInsets = ((JComponent) c).getInsets();
                size -= cInsets.top + cInsets.bottom;
                Insets bInsets = iconBox_.getInsets();
                size -= bInsets.top + bInsets.bottom;
            }
            iconBox_.setTransverseSize( size );
        }
        else {
            iconListModel_.setClient( null );
        }
        return c;
    }

    /**
     * Returns the position at which the transmission list icon should
     * be drawn.
     *
     * @return   icon base position
     */
    private Point getIconBoxPosition() {
        Insets insets = getInsets();
        return new Point( insets.left + super.getPreferredSize().width
                                      + msgGap_,
                          insets.top );
    }

    /**
     * ListModel which can be used in the icon box.
     * It contains entries for each received and sent message, as well as
     * one which notionally represents the application (visual sugar).
     * It is basically a combination of the rx and tx models.
     */
    private class IconListModel implements ListModel {
        Client client_;
        ListModel rxModel_;
        ListModel txModel_;
        private final ListDataListener rxListener_;
        private final ListDataListener txListener_;
        private final List listenerList_;

        /**
         * Constructor.
         */
        IconListModel() {
            listenerList_ = new ArrayList();
            rxListener_ = new ListDataForwarder() {
                public int getOffset() {
                    return 0;
                }
            };
            txListener_ = new ListDataForwarder() {
                public int getOffset() {
                    return ( rxModel_ == null ? 0 : rxModel_.getSize() ) + 1;
                }
            };
        }

        /**
         * Sets the client whose transmissions this list will represent.
         * May be null.
         *
         * @param   client  client
         */
        public void setClient( Client client ) {
            if ( rxModel_ != null ) {
                rxModel_.removeListDataListener( rxListener_ );
            }
            if ( txModel_ != null ) {
                txModel_.removeListDataListener( txListener_ );
            }
            client_ = client;
            rxModel_ = transHolder_.getRxListModel( client );
            txModel_ = transHolder_.getTxListModel( client );
            if ( rxModel_ != null ) {
                rxModel_.addListDataListener( rxListener_ );
            }
            if ( txModel_ != null ) {
                txModel_.addListDataListener( txListener_ );
            }
            fireEvent( new ListDataEvent( this, ListDataEvent.CONTENTS_CHANGED,
                                          -1, -1 ) );
        }

        public int getSize() {
            return ( rxModel_ == null ? 0 : rxModel_.getSize() )
                 + 1
                 + ( txModel_ == null ? 0 : txModel_.getSize() );
        }

        public Object getElementAt( int index ) {
            int rxSize = rxModel_ == null ? 0 : rxModel_.getSize();
            if ( index < rxSize ) {
                return rxModel_.getElementAt( index );
            }
            index -= rxSize;

            if ( index < 1 ) {
                return separator_;
            }
            index -= 1;

            int txSize = txModel_ == null ? 0 : txModel_.getSize();
            if ( index < txSize ) {
                return txModel_.getElementAt( index );
            }
            index -= txSize;

            throw new IllegalArgumentException();
        }

        public void addListDataListener( ListDataListener listener ) {
            listenerList_.add( listener );
        }

        public void removeListDataListener( ListDataListener listener ) {
            listenerList_.remove( listener );
        }

        /**
         * Passes an event on to registered ListDataListeners.
         */
        private void fireEvent( ListDataEvent evt ) {
            for ( Iterator it = listenerList_.iterator(); it.hasNext(); ) {
                ListDataListener listener = (ListDataListener) it.next();
                switch ( evt.getType() ) {
                    case ListDataEvent.INTERVAL_ADDED:
                        listener.intervalAdded( evt );
                        break;
                    case ListDataEvent.INTERVAL_REMOVED:
                        listener.intervalRemoved( evt );
                        break;
                    case ListDataEvent.CONTENTS_CHANGED:
                        listener.contentsChanged( evt );
                        break;
                    default:
                        assert false;
                }
            }
        }

        /**
         * Listener implementation which can listen to constituent (rx and tx)
         * models and forward events from them to listeners to this model.
         */
        private abstract class ListDataForwarder implements ListDataListener {

            /**
             * Returns the offset into the IconBoxModel at which the
             * model this listener is listening to starts.
             *
             * @return  model element offset
             */
            abstract int getOffset();

            public void intervalAdded( ListDataEvent evt ) {
                forwardEvent( evt );
            }

            public void intervalRemoved( ListDataEvent evt ) {
                forwardEvent( evt );
            }

            public void contentsChanged( ListDataEvent evt ) {
                forwardEvent( evt );
            }

            /**
             * Takes an event received by this listener, adjusts its
             * indexes appropriately, and forwards it to listeners to this
             * model.
             *
             * @param  evt   event to forward
             */
            private void forwardEvent( ListDataEvent evt ) {
                Object src = evt.getSource();
                int i0 = evt.getIndex0();
                int i1 = evt.getIndex1();
                if ( 0 <= i0 && i0 <= i1 ) {
                    int offset = getOffset();
                    fireEvent( new ListDataEvent( evt.getSource(),
                                                  evt.getType(),
                                                  evt.getIndex0() + offset,
                                                  evt.getIndex1() + offset ) );
                }
                else {
                    fireEvent( evt );
                }
            }
        }
    }
}
