package org.astrogrid.samp.gui;

import java.awt.Component;
import java.util.EventObject;
import javax.swing.SwingUtilities;
import org.astrogrid.samp.client.SampException;

/**
 * Helper class for performing a SAMP operation from within the
 * Event Dispatch Thread.
 * You must implement the {@link #runSamp} method to do the work;
 * any resulting SampException will be suitably displayed to the user.
 *
 * @author   Mark Taylor
 * @since    27 Jul 2011
 */
abstract class SampThread extends Thread {

    private final Component parent_;
    private final String errTitle_;
    private final String errText_;

    /**
     * Constructs a SampThread given a parent component.
     * Arguments are required for posting an error if one occurs.
     *
     * @param   parent   parent component
     * @param   errTitle  title of error window if one is needed
     * @param   errText   text of error messsage if one is needed
     */
    public SampThread( Component parent, String errTitle, String errText ) {
        super( "SAMP call" );
        parent_ = parent;
        errTitle_ = errTitle;
        errText_ = errText;
    }

    /**
     * Constructs a SampThread given an event object with a source which
     * presumably corresponds to a parent component.
     *
     * @param  evt  triggering event
     * @param   errTitle  title of error window if one is needed
     * @param   errText   text of error messsage if one is needed
     */
    public SampThread( EventObject evt, String errTitle, String errText ) {
        this( evt.getSource() instanceof Component ? (Component) evt.getSource()
                                                   : null,
              errTitle, errText );
    }

    /**
     * Called from the {@link #run} method.
     */
    protected abstract void sampRun() throws SampException;

    public void run() {
        try {
            sampRun();
        }
        catch ( final SampException e ) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    ErrorDialog.showError( parent_, errTitle_, errText_, e );
                }
            } );
        }
    }
}
