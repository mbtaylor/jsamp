package org.astrogrid.samp.web;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Window;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * ClientAuthorizer implementation that queries the user for permission
 * via a popup dialogue.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class HubSwingClientAuthorizer implements ClientAuthorizer {

    private final Component parent_;
    private static final int MAX_POPUP_WIDTH = 500;
    private static final Logger logger_ =
        Logger.getLogger( HubSwingClientAuthorizer.class.getName() );

    /**
     * Constructor.
     *
     * @param  parent  parent component
     */
    public HubSwingClientAuthorizer( Component parent ) {
        parent_ = parent;
        if ( GraphicsEnvironment.isHeadless() ) {
            throw new HeadlessException( "Client authorization dialogues "
                                       + "impossible - no graphics" );
        }
    }

    public boolean authorize( HttpServer.Request request, String appName ) {

        // Prepare an internationalised query dialogue.
        AuthResourceBundle.Content authContent =
            AuthResourceBundle
           .getAuthContent( ResourceBundle
                           .getBundle( AuthResourceBundle.class.getName() ) );
        Object[] qmsg = getMessageLines( request, appName, authContent );
        String noOpt = authContent.noWord();
        String yesOpt = authContent.yesWord();

        // Just calling showOptionDialog can end up with the popup being
        // obscured by other windows on the desktop, at least for win XP.
        JOptionPane jop =
            new JOptionPane( qmsg, JOptionPane.WARNING_MESSAGE,
                             JOptionPane.YES_NO_OPTION, null,
                             new String[] { noOpt, yesOpt }, noOpt );
        JDialog dialog = jop.createDialog( parent_, authContent.windowTitle() );
        attemptSetAlwaysOnTop( dialog, true );
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
     * Returns a "message" object describing the applying client to the user.
     * The return value is suitable for use as the <code>msg</code> argument
     * of one of <code>JOptionPane</code>'s methods.
     * 
     * @param  request  HTTP request bearing the application
     * @param   appName  application name claimed by the applicant
     * @param   authContent  content of AuthResourceBundle bundle
     * @return   message array describing the applicant to the user
     * @see   javax.swing.JOptionPane
     */
    private Object[] getMessageLines( HttpServer.Request request,
                                      String appName,
                                      AuthResourceBundle.Content authContent ) {
        Map headerMap = request.getHeaderMap();

        // Application origin (see http://www.w3.org/TR/cors/,
        // http://tools.ietf.org/html/draft-abarth-origin);
        // present if CORS is in use.
        String origin = HttpServer.getHeader( headerMap, "Origin" );

        // Referer header (RFC2616 sec 14.36) - present at whim of browser.
        String referer = HttpServer.getHeader( headerMap, "Referer" );

        List lineList = new ArrayList();
        lineList.addAll( toLineList( authContent.appIntroductionLines() ) );
        lineList.add( "\n" );
        Map infoMap = new LinkedHashMap();
        infoMap.put( authContent.nameWord(), appName );
        infoMap.put( authContent.originWord(), origin );
        infoMap.put( "URL", referer );
        lineList.add( createLabelledFields( infoMap,
                                            authContent.undeclaredWord() ) );
        lineList.add( "\n" );
        if ( referer != null && origin != null &&
             ! origin.equals( getOrigin( referer ) ) ) {
            logger_.warning( "Origin/Referer header mismatch: "
                           + "\"" + origin + "\" != "
                           + "\"" + getOrigin( referer ) + "\"" );
            lineList.add( "WARNING: Origin/Referer header mismatch!" );
            lineList.add( "WARNING: This looks suspicious." );
            lineList.add( "\n" );
        }
        lineList.addAll( toLineList( authContent.privilegeWarningLines() ) );
        lineList.add( "\n" );
        lineList.addAll( toLineList( authContent.adviceLines() ) );
        lineList.add( "\n" );
        lineList.add( authContent.questionLine() );
        return lineList.toArray();
    }

    /**
     * Returns a component displaying name/value pairs represented by
     * a given String->String map.
     *
     * @param  infoMap  String->String map of key->value pairs
     * @param  undeclaredWord  text to use to indicate a null value
     * @return   display component
     */
    private JComponent createLabelledFields( Map infoMap,
                                             String undeclaredWord ) {
        GridBagLayout layer = new GridBagLayout();
        JComponent box = new JPanel( layer ) {
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension( Math.min( size.width, MAX_POPUP_WIDTH ),
                                      size.height );
            }
        };
        GridBagConstraints keyCons = new GridBagConstraints();
        GridBagConstraints valCons = new GridBagConstraints();
        keyCons.gridy = 0;
        valCons.gridy = 0;
        keyCons.gridx = 0;
        valCons.gridx = 1;
        keyCons.anchor = GridBagConstraints.WEST;
        valCons.anchor = GridBagConstraints.WEST;
        keyCons.fill = GridBagConstraints.NONE;
        valCons.fill = GridBagConstraints.HORIZONTAL;
        keyCons.weighty = 1;
        valCons.weighty = 1;
        keyCons.weightx = 0;
        valCons.weightx = 1;
        valCons.insets = new Insets( 1, 1, 1, 1 );

        JComponent stack = Box.createVerticalBox();
        for ( Iterator it = infoMap.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            String value = (String) infoMap.get( key );
            String valtxt = value == null ? undeclaredWord : value;
            JComponent keyComp = new JLabel( key + ": " );
            JTextField valueField = new JTextField( valtxt );
            valueField.setEditable( false );
            layer.setConstraints( keyComp, keyCons );
            layer.setConstraints( valueField, valCons );
            box.add( keyComp );
            box.add( valueField );
            keyCons.gridy++;
            valCons.gridy++;
        }
        box.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, 0 ) );
        return box;
    }

    /**
     * Returns the serialized origin for a given URI string.
     * @see <a href="http://tools.ietf.org/html/draft-abarth-origin-09"
     *         >The Web Origin Concept</a>
     *
     * @param  uri  URI
     * @return  origin of <code>uri</code>,
     *          <code>null</code> (note: not "null") if it cannot be determined
     */
    private String getOrigin( String uri ) {
        if ( uri == null ) {
            return null;
        }
        URL url;
        try {
            url = new URL( uri );
        }
        catch ( MalformedURLException e ) {
            return null;
        }
        String scheme = url.getProtocol();
        String host = url.getHost();
        int portnum = url.getPort();
        StringBuffer sbuf = new StringBuffer()
            .append( scheme )
            .append( "://" )
            .append( host );
        if ( portnum >= 0 && portnum != url.getDefaultPort() ) {
            sbuf.append( ":" )
                .append( Integer.toString( portnum ) );
        }
        return sbuf.toString().toLowerCase();
    }

    /**
     * Turns a multi-line string into an array of strings.
     *
     * @param  linesTxt  string perhaps with embedded \n characters
     * @return  array of lines
     */
    private static String[] toLines( String linesTxt ) {
        return linesTxt.split( "\\n" );
    }

    /**
     * Turns a multi-line string into a List of strings.
     *
     * @param  linesTxt  string perhaps with embedded \n characters
     * @return  list of String lines
     */
    private static List toLineList( String linesTxt ) {
        return Arrays.asList( toLines( linesTxt ) );
    }

    /**
     * Tries to set the always-on-top property of a window.
     * This is only possible in JRE1.5 and later, so it's done here by
     * reflection.  If it fails, a logging message is emitted.
     *
     * @param   win  window to set
     * @param  isOnTop  true for on top, false for not
     */
    private static void attemptSetAlwaysOnTop( Window win, boolean isOnTop ) {
        try {
            Window.class.getMethod( "setAlwaysOnTop",
                                    new Class[] { boolean.class } )
                  .invoke( win, new Object[] { Boolean.valueOf( isOnTop ) } );
        }
        catch ( Throwable e ) {
            logger_.info( "Can't set window on top, not J2SE5" );
        }
    }
}
