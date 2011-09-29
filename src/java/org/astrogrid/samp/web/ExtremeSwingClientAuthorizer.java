package org.astrogrid.samp.web;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.border.Border;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.gui.IconStore;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * Client authorizer implementaion that does its very best to discourage
 * users from accepting regitrations.
 *
 * @author   Mark Taylor
 * @since    29 Sep 2011
 */
public class ExtremeSwingClientAuthorizer implements ClientAuthorizer {

    private final Component parent_;

    /**
     * Constructor.
     *
     * @param  parent  parent component, may be null
     */
    public ExtremeSwingClientAuthorizer( Component parent ) {
        parent_ = parent;
        if ( GraphicsEnvironment.isHeadless() ) {
            throw new HeadlessException( "No graphics - lucky escape" );
        }
    }

    public boolean authorize( HttpServer.Request request, String appName ) {
        JComponent panel = Box.createVerticalBox();
        JComponent linePic = Box.createHorizontalBox();
        URL imageUrl = Client.class.getResource( "images/danger.jpeg" );
        linePic.add( Box.createHorizontalGlue() );
        linePic.add( new JLabel( new ImageIcon( imageUrl ) ) );
        linePic.add( Box.createHorizontalGlue() );
        panel.add( linePic );
        panel.add( Box.createVerticalStrut( 5 ) );
        JComponent line1 = Box.createHorizontalBox();
        line1.add( new JLabel( "Client \"" + appName
                             + "\" is requesting Web Profile registration." ) );
        line1.add( Box.createHorizontalGlue() );
        line1.setBorder( createBorder( false ) );
        panel.add( line1 );
        JLabel deathLabel = new JLabel( "CERTAIN DEATH" );
        deathLabel.setForeground( Color.RED );
        deathLabel.setFont( deathLabel.getFont()
                           .deriveFont( deathLabel.getFont().getSize() + 2f ) );
        JComponent line2 = Box.createHorizontalBox();
        line2.add( new JLabel( "Accepting this request will lead to " ) );
        line2.add( deathLabel );
        line2.add( new JLabel( "!" ) );
        line2.add( Box.createHorizontalGlue() );
        line2.setBorder( createBorder( true ) );
        panel.add( line2 );
        return JOptionPane
              .showOptionDialog( parent_, panel, "Registration Request",
                                 JOptionPane.YES_NO_OPTION,
                                 JOptionPane.QUESTION_MESSAGE,
                                 IconStore.createEmptyIcon( 0 ),
                                 new String[] { "Accept", "Reject" },
                                 "Reject" ) == 0;
    }

    /**
     * Returns a new border of fixed dimensions which may or may not include
     * an element of highlighting.
     *
     * @param   highlight  true to highlight border
     * @return  new border
     */
    private Border createBorder( boolean highlight ) {
        Color color = new Color( 0x00ff0000, ! highlight );
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder( new Color( 0xff0000, !highlight ) ),
            BorderFactory.createEmptyBorder( 2, 2, 2, 2 )
        );
    }
}
