package org.astrogrid.samp.web;

import java.awt.Component;
import javax.swing.JOptionPane;

/**
 * OriginAuthorizer which uses a popup dialogue to ask the user.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
class SwingOriginAuthorizer implements OriginAuthorizer {

    private final Component parent_;

    /**
     * Constructor.
     *
     * @param  parent  parent component
     */
    public SwingOriginAuthorizer( Component parent ) {
        parent_ = parent;
    }

    public boolean authorize( String origin ) {
        return getResponse( new String[] {
            "Is the following origin authorized for cross-domain HTTP access?",
            "    " + origin,
        } );
    }

    public boolean authorizeAll() {
        return getResponse( new String[] {
            "Are all origins authorized for cross-domain HTTP access?",
        } );
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
        return JOptionPane
              .showOptionDialog( parent_, lines, "Security",
                                 JOptionPane.YES_NO_OPTION,
                                 JOptionPane.QUESTION_MESSAGE, null,
                                 new String[] { "Yes", "No" }, "No" )
               == 0;
    }
}
