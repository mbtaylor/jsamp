package org.astrogrid.samp.web;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * ClientAuthorizer implementation that queries the user for permission
 * via a popup dialogue.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class SwingClientAuthorizer implements ClientAuthorizer {

    private final Component parent_;
    private static final Logger logger_ =
        Logger.getLogger( SwingClientAuthorizer.class.getName() );

    /**
     * Constructor.
     *
     * @param  parent  parent component
     */
    public SwingClientAuthorizer( Component parent ) {
        parent_ = parent;
        if ( GraphicsEnvironment.isHeadless() ) {
            throw new HeadlessException( "Client authorization dialogues "
                                       + "impossible - no graphics" );
        }
    }

    public boolean authorize( HttpServer.Request request, String appName ) {
        List lineList = new ArrayList();
        lineList.add( "The following application, "
                    + "probably running in a browser," );
        lineList.add( "is requesting cross-domain HTTP access:" );
        lineList.add( "\n" );
        lineList.addAll( Arrays.asList( applicantLines( request, appName ) ) );
        lineList.add( "\n" );
        lineList.add( "If you permit this, it will have most "
                    + "of the privileges" );
        lineList.add( "of user " + System.getProperty( "user.name" ) 
                    + ", such as file read/write." );
        lineList.add( "\n" );
        lineList.add( "Do you authorise connection?" );
        return getResponse( (String[]) lineList.toArray( new String[ 0 ] ) );
    }

    /**
     * Returns some lines of text describing the applying client.
     * Suitable for directing to the user.
     * 
     * @param  request  HTTP request bearing the application
     * @param   appName  application name claimed by the applicant
     * @return   array of lines of plain text suitable for describing the
     *           applicant to the user; not too long; each element
     *           represents a screen line
     */
    protected String[] applicantLines( HttpServer.Request request,
                                       String appName ) {

        // Application name.
        List lineList = new ArrayList();
        lineList.add( "    Name: " + appName );

        // Application origin (see http://www.w3.org/TR/cors/,
        // http://tools.ietf.org/html/draft-abarth-origin).
        String origin =
            HttpServer.getHeader( request.getHeaderMap(), "Origin" );
        if ( origin == null ) {
            origin = "undeclared";
        }
        lineList.add( "    Origin: " + origin );

        // Return text lines.
        return (String[]) lineList.toArray( new String[ 0 ] );
    }

    /**
     * Presents some lines of text to the user and solicits a yes/no
     * response from them.
     * This method does not need to be called from the AWT event dispatch
     * thread.
     *
     * @param   lines  lines of formatted plain text
     *          (not too many; not too long)
     * @return   true/false for use yes/no response
     */
    protected boolean getResponse( String[] lines ) {
        String noOpt = "No";
        String yesOpt = "Yes";

        // Just calling showOptionDialog can end up with the popup being
        // obscured by other windows on the desktop, at least for win XP.
        JOptionPane jop =
            new JOptionPane( lines, JOptionPane.WARNING_MESSAGE,
                             JOptionPane.YES_NO_OPTION, null,
                             new String[] { yesOpt, noOpt }, noOpt );
        JDialog dialog = jop.createDialog( parent_, "SAMP Hub Security" );
        dialog.setAlwaysOnTop( true );
        dialog.setModal( true );
        dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );

        // It seems to be OK to call Dialog.setVisible on a modal dialogue
        // from threads other than the AWT Event Dispatch Thread.
        // I admit though that I haven't seen document which assures that
        // this is true however.
        dialog.setVisible( true );
        dialog.dispose();
        return jop.getValue() == yesOpt;
    }
}
