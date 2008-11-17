package org.astrogrid.samp.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Metadata;

/**
 * Manages client icons.  The {@link #getClientIcon} method returns the icon
 * associated with a given client.  Images are cached where appropriate.
 * A size may be supplied so that all icons returned by this object's methods
 * are of a given standard size.
 * Also provides some icon utility methods.
 *
 * @author   Mark Taylor
 * @since    17 Nov 2008
 */
public class IconStore {

    private final int size_;
    private final Icon defaultIcon_;

    private static final Map urlIconMap_ = new HashMap();
    private static final Logger logger_ =
        Logger.getLogger( IconStore.class.getName() );

    /**
     * Constructor.
     *
     * @param  size  dimension in pixels of icons returned by this object;
     *               if &lt;=0 no resizing is performed
     * @param  defaultIcon   icon returned if no client icon is available
     */
    public IconStore( int size, Icon defaultIcon ) {
        size_ = size;
        defaultIcon_ = defaultIcon;
    }

    /**
     * Returns the icon supplied by the graphic file at a given URL.
     * Icons are cached, so that repeated invocations with the same url
     * are not expensive.
     *
     * @param  url  URL of image
     * @return  image icon, resized if appropriate
     */
    public Icon getIcon( String url ) {
        if ( ! urlIconMap_.containsKey( url ) ) {
            try {
                Icon icon = new ImageIcon( new URL( url ) );
                synchronized ( urlIconMap_ ) {
                    urlIconMap_.put( url, icon );
                }
            }
            catch ( MalformedURLException e ) {
                synchronized ( urlIconMap_ ) {
                    urlIconMap_.put( url, defaultIcon_ );
                }
            }
        }
        Icon icon = (Icon) urlIconMap_.get( url );
        if ( icon.getIconWidth() < 0 ) {
            icon = defaultIcon_;
        }
        if ( size_ > 0 && icon != null ) {
            icon = sizeIcon( icon, size_ );
        }
        return icon;
    }

    /**
     * Returns the icon associated with a given client.
     * This is either the icon described in its metadata or the default icon
     * if there isn't one.
     *
     * @param   client   client whose icon is required
     * @return  associated icon, resized if appropriate
     */
    public Icon getIcon( Client client ) {
        Metadata meta = client.getMetadata();
        if ( meta != null ) {
            Object url = meta.get( Metadata.ICONURL_KEY );
            if ( url instanceof String ) {
                return getIcon( (String) url );
            }
        }
        return defaultIcon_;
    }

    /**
     * Returns an icon with no content but a given size.
     *
     * @param  size  edge size in pixels
     * @return  emtpy square icon
     */
    public static Icon createEmptyIcon( final int size ) {
        return new Icon() {
            public int getIconWidth() {
                return size;
            }
            public int getIconHeight() {
                return size;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
            }
        };
    }

    /**
     * Returns an icon which indicates a shape but doesn't look like much.
     * Currently it's a kind of open square.
     *
     * @param  size  dimension in pixels
     * @return    minimal icon
     */
    public static Icon createMinimalIcon( final int size ) {
        return new Icon() {
            int gap = 2;
            int size = 24;
            public int getIconWidth() {
                return size;
            }
            public int getIconHeight() {
                return size;
            }
            public void paintIcon( Component c, Graphics g, int x, int y ) {
                Color color = g.getColor();
                int nl = 5;
                for ( int i = 0; i < nl; i++ ) {
                    int lo = gap + i;
                    int dim = size - 2 * ( gap + i );
                    if ( dim <= 0 ) {
                        break;
                    }
                    int glevel = 255 * ( i + 1 ) / ( nl + 1 );
                    g.setColor( new Color( glevel, glevel, glevel ) );
                    g.drawRect( x + lo, y + lo, dim, dim );
                }
                g.setColor( color );
            } 
        };
    }

    /**
     * Constructs an icon given a file name in the images directory.
     *
     * @param  fileName  file name omitting directory
     * @return  icon 
     */
    static Icon createResourceIcon( String fileName ) {
        String relLoc = "images/" + fileName;
        URL resource = Client.class.getResource( relLoc );
        if ( resource != null ) {
            return new ImageIcon( resource );
        }
        else {
            logger_.warning( "Failed to load icon " + relLoc );
            return new Icon() { 
                public int getIconWidth() {
                    return 24;
                }
                public int getIconHeight() {
                    return 24;
                }
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                }
            };
        }
    }

    /**
     * Return an icon based on an existing one, but drawn to an exact size.
     *
     * @param  icon  original icon, or null for blank
     * @param  size  number of horizontal and vertical pixels in output
     * @return  resized version of <code>icon</code>
     */
    public static Icon sizeIcon( Icon icon, final int size ) {
        if ( icon == null ) {
            return new Icon() {
                public int getIconWidth() {
                    return size;
                }
                public int getIconHeight() {
                    return size;
                }
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                }
            };
        }
        else if ( icon.getIconWidth() == size &&
                  icon.getIconHeight() == size ) {
            return icon;
        }
        else {
            return new SizedIcon( icon, size );
        }
    }

    /**
     * Icon implementation which looks like an existing one, but is resized
     * down if necessary.
     */
    private static class SizedIcon implements Icon {
        private final Icon icon_;
        private final int size_; 
        private final double factor_;

        /**
         * Constructor.
         *
         * @param   icon  original icon
         * @param   size  number of horizontal and vertical pixels in this icon
         */
        public SizedIcon( Icon icon, int size ) {
            icon_ = icon;
            size_ = size;
            factor_ =
                Math.min( 1.0,
                          Math.min( size / (double) icon.getIconWidth(),
                                    size / (double) icon.getIconHeight() ) );
        }

        public int getIconWidth() {
            return size_;
        }

        public int getIconHeight() { 
            return size_;
        }

        public void paintIcon( Component c, Graphics g, int x, int y ) {
            int iw = icon_.getIconWidth();
            int ih = icon_.getIconHeight();
            if ( factor_ == 1.0 ) {
                icon_.paintIcon( c, g, x + ( size_ - iw ) / 2,
                                       y +  ( size_ - ih ) / 2 );
            }
            else {
                Graphics2D g2 = (Graphics2D) g;
                AffineTransform trans = g2.getTransform();
                g2.translate( x + ( size_ - iw * factor_ ) / 2,
                              y + ( size_ - ih * factor_ ) / 2 );
                g2.scale( factor_, factor_ );
                icon_.paintIcon( c, g2, 0, 0 );
                g2.setTransform( trans );
            }
        }
    }
}
