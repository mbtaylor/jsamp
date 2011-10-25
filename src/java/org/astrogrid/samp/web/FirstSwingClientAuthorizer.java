package org.astrogrid.samp.web;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * Client authorizer that appears to allow the Web Profile to be
 * activated the first time it is used.  Actually this is not really
 * what happens.  There are a few problems with this procedure,
 * and it probably does not provide any useful functionality.
 * Only ever used for experimental/demonstration purposes.
 *
 * @author   Mark Taylor
 * @since    25 Oct 2011
 */
public class FirstSwingClientAuthorizer implements ClientAuthorizer {

    private final Component parent_;
    private final ClientAuthorizer baseAuthorizer_;
    private WebHubProfile hubProfile_;
    private boolean hasConfirmed_;
    private static final Logger logger_ =
        Logger.getLogger( FirstSwingClientAuthorizer.class.getName() );

    public FirstSwingClientAuthorizer( Component parent,
                                       ClientAuthorizer baseAuthorizer ) {
        if ( GraphicsEnvironment.isHeadless() ) {
            throw new HeadlessException( "Client authorization dialogues "
                                       + "impossible - no graphics" );
        }
        parent_ = parent;
        baseAuthorizer_ = baseAuthorizer;
    }

    public void setHubProfile( WebHubProfile hubProfile ) {
        hubProfile_ = hubProfile;
    }

    public synchronized boolean authorize( HttpServer.Request request,
                                           String appName ) {
        if ( hubProfile_ == null ) {
            logger_.warning( "Hub profile not installed into ClientAuthorizer "
                           + this );
            return false;
        }
        if ( ! hasConfirmed_ ) {
            if ( askUserIfOn() ) {
                hasConfirmed_ = true;
            }
            else {
                hubProfile_.stop();
                return false;
            }
        }
        return baseAuthorizer_.authorize( request, appName );
    }

    protected boolean askUserIfOn() {
        String[] lines = new String[] {
            "A web application is asking to use the SAMP Web Profile.",
            "The Web Profile is currently not switched on.",
            " ",
            "Switching the Web Profile on opens you to certain security risks",
            "but allows web pages to talk to desktop applications.",
            " ",
            "You may choose to switch it on if you wish.",
            "If so, you will be asked individually about any web apps",
            "wanting access.  If not, they will all be blocked.",
            " ",
            "Do you wish to switch on the SAMP Web Profile?",
        };
        String noOpt = "No";
        String yesOpt = "Yes";
        JOptionPane jop =
            new JOptionPane( lines, JOptionPane.QUESTION_MESSAGE,
                             JOptionPane.YES_NO_OPTION, null,
                             new String[] { noOpt, yesOpt }, noOpt );
        JDialog dialog = jop.createDialog( parent_, "Web Profile Activation" );
        dialog.setAlwaysOnTop( true );
        dialog.setModal( true );
        dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
        dialog.setVisible( true );
        dialog.dispose();
        return jop.getValue() == yesOpt;
    }
}
