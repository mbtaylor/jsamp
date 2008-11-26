package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ToolTipManager;
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
    private final int padding_;
    private Client client_;
    private TransmissionListIcon transListIcon_;

    /**
     * Constructor.
     *
     * @param   transHolder  obtains list models containing sent/received
     *          messages
     */
    public MessageTrackerListCellRenderer(
            ClientTransmissionHolder transHolder ) {
        transHolder_ = transHolder;
        padding_ = 10;
    }

    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        if ( transListIcon_ != null ) {
            Point p = getIconPosition();
            if ( g.hitClip( p.x, p.y, transListIcon_.getIconWidth(),
                                      transListIcon_.getIconHeight() ) ) {
                transListIcon_.paintIcon( this, g, p.x, p.y );
            }
        }
    }

    public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        if ( transListIcon_ != null ) {
            prefSize.width += transListIcon_.getIconWidth() + padding_;
        }
        return prefSize;
    }

    public String getToolTipText( MouseEvent evt ) {
        if ( transListIcon_ != null ) {
            Point iconPos = getIconPosition();
            Point p = new Point( evt.getPoint() );
            p.x -= iconPos.x;
            p.y -= iconPos.y;
            Transmission trans = transListIcon_.getTransmissionAt( p );
            if ( trans != null ) {
                return getToolTipText( trans );
            }
        }
        return null;
    }

    /**
     * Returns the tooltip to use for a given transmission.
     *
     * @param   trans   non-null transmission
     * @return  tool tip
     */
    protected String getToolTipText( Transmission trans ) {
        String mtype = trans.getMessage().getMType();
        if ( client_ == trans.getSender() ) {
            return mtype + " -> "
                 + SampUtils.toString( trans.getReceiver() );
        }
        else if ( client_ == trans.getReceiver() ) {
            return mtype + " <- "
                 + SampUtils.toString( trans.getSender() );
        }
        else {
            assert false;
            return null;
        }
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( value instanceof Client ) {
            client_ = (Client) value;
            int size = c.getPreferredSize().height;
            if ( c instanceof JComponent ) {
                Insets insets = ((JComponent) c).getInsets();
                size -= insets.top + insets.bottom;
            }
            transListIcon_ =
                new TransmissionListIcon( transHolder_
                                         .getRxListModel( client_ ),
                                          transHolder_
                                         .getTxListModel( client_ ), size );
        }
        else {
            transListIcon_ = null;
        }
        return c;
    }

    /**
     * Returns the position at which the transmission list icon should
     * be drawn.
     *
     * @return   icon base position
     */
    private Point getIconPosition() {
        Insets insets = getInsets();
        return new Point( insets.left + super.getPreferredSize().width
                                      + padding_,
                          insets.top );
    }
}
