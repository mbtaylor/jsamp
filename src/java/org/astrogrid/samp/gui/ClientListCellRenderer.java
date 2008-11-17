package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListModel;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;

/**
 * List Cell Renderer for use with {@link org.astrogrid.samp.Client} objects.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class ClientListCellRenderer extends DefaultListCellRenderer {

    private final ClientLabeller labeller_;
    private boolean useNicknames_;
    private Font[] labelFonts_;
    private IconStore iconStore_;

    /**
     * Constructor.
     *
     * @param  listModel  list model whose elements we will be rendering
     * @param  regInfo    registration information for hub connection to
     *                    which clients apply (may be null)
     */
    public ClientListCellRenderer( ListModel listModel, RegInfo regInfo ) {
        labeller_ = new ClientLabeller( listModel, regInfo );
        iconStore_ = new IconStore( -1, IconStore.createEmptyIcon( 16 ) );
    }

    /**
     * Determine whether the textual representation of clients uses a
     * nickname-type format or not.
     * If nicknames are used, an attempt is made to give clients short names
     * based on their metadata and some sort of disambiguation index.
     * If not, the client public ID will always be shown.
     *
     * @param  useNicknames  whether to use nicknames
     */
    public void setUseNicknames( boolean useNicknames ) {
        useNicknames_ = useNicknames;
    }

    public Component getListCellRendererComponent( JList list, Object value,
                                                   int index, boolean isSel,
                                                   boolean hasFocus ) {
        Component c = super.getListCellRendererComponent( list, value, index,
                                                          isSel, hasFocus );
        if ( c instanceof JLabel && value instanceof Client ) {
            JLabel jl = (JLabel) c;
            Client client = (Client) value;
            String id = client.getId();
            final String text;
            final Font font;
            if ( useNicknames_ ) {
                String label = labeller_.getLabel( client );
                text = label == null ? id : label;
                font = getLabelFont( label == null );
            }
            else {
                StringBuffer sbuf = new StringBuffer();
                Metadata meta = client.getMetadata();
                if ( meta != null ) {
                    String name = meta.getName();
                    if ( name != null && name.trim().length() > 0 ) {
                        sbuf.append( name )
                            .append( ' ' );
                    }
                }
                sbuf.append( '(' )
                    .append( id )
                    .append( ')' );
                text = sbuf.toString();
                font = getLabelFont( false );
            }
            int size;
            try {
                size = (int)
                    Math.ceil( font.getMaxCharBounds( ((Graphics2D)
                                                       list.getGraphics())
                                                     .getFontRenderContext() )
                                   .getHeight() );
            }
            catch ( NullPointerException e ) {
                size = 16;
            }
            jl.setText( text );
            jl.setFont( font );
            jl.setIcon( IconStore
                       .sizeIcon( iconStore_.getIcon( client ), size ) );
        }
        return c;
    }

    /**
     * Returns the font used by this label, or a variant.
     *
     * @param   special   true if the font is to look a bit different
     * @return  font
     */
    private Font getLabelFont( boolean special ) {
        if ( labelFonts_ == null ) {
            Font normalFont = getFont().deriveFont( Font.BOLD );
            Font aliasFont = getFont().deriveFont( Font.PLAIN );
            labelFonts_ = new Font[] { normalFont, aliasFont };
        }
        return labelFonts_[ special ? 1 : 0 ];
    }
}
