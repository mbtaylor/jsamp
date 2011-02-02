package org.astrogrid.samp.web;

import java.awt.Component;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
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
    private final boolean permitRemote_;
    private static final Logger logger_ =
        Logger.getLogger( SwingClientAuthorizer.class.getName() );

    /**
     * Constructor.
     *
     * @param  parent  parent component
     * @param   permitRemote  true iff clients from non-local hosts can be
     *          authorized; if false, they will be rejected without asking
     *          the user (this is usually a good idea)
     */
    public SwingClientAuthorizer( Component parent, boolean permitRemote ) {
        parent_ = parent;
        permitRemote_ = permitRemote;
    }

    public boolean authorize( HttpServer.Request request, String appName ) {
        if ( ! checkAddress( request, appName ) ) {
            return false;
        }
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
     * Indicates whether an application should be rejected out of hand
     * because of characteristics of its HTTP request.
     * False will be returned if it cannot be accepted, for instance if
     * it comes from a non-local host, and if non-local hosts are blocked.
     *
     * @param   request   HTTP request making the application
     * @param   appName   name claimed by the applicant
     * @return  true if the application <em>may</em> be successful,
     *          false if it should definitely be rejected
     */
    public boolean checkAddress( HttpServer.Request request, String appName ) {
        if ( permitRemote_ ) {
            return true;
        }
        else {
            SocketAddress sock = request.getRemoteAddress();
            InetAddress address = sock instanceof InetSocketAddress
                                ? ((InetSocketAddress) sock).getAddress()
                                : null;
            if ( address == null ) {
                logger_.warning( "Reject registration request for " + appName
                               + "; address not on internet" );
                return false;
            }
            else if ( ! isLocalHost( address ) ) {
                logger_.warning( "Reject registration request for " + appName
                               + "; address is not localhost" );
                return false;
            }
            assert isLocalHost( address );
            return true;
        }
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

        // Warn about non-local origin if appropriate.
        if ( permitRemote_ ) {
            SocketAddress sock = request.getRemoteAddress();
            InetAddress address = sock instanceof InetSocketAddress
                                ? ((InetSocketAddress) sock).getAddress()
                                : null;
            boolean isLocal = isLocalHost( address );
            String host;
            if ( isLocal ) {
                host = "local host";
            }
            else if ( address != null ) {
                host = address.getHostName();
            }
            else {
                host = "unknown host";
            }
            lineList.add( "    Host: " + host );
            if ( ! isLocal ) {
                lineList.add( "Warning: the requesting application "
                            + "is not running on your local host." );
            }
        }

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
            new JOptionPane( lines, JOptionPane.QUESTION_MESSAGE,
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

    /**
     * Indicates whether a network address is known to represent the local host.
     *
     * @param   address  internet address
     * @return  true  iff address is known to be local
     */
    public static boolean isLocalHost( InetAddress address ) {
        if ( address != null ) {
            if ( address.isLoopbackAddress() ) {
                return true;
            }
            else {
                try {
                    return address.equals( InetAddress.getLocalHost() );
                }
                catch ( UnknownHostException e ) {
                    return false;
                }
            }
        }
        else {
            return false;
        }
    }
}
