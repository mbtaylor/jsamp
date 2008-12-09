package org.astrogrid.samp.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * ListModel implementation for containing {@link Transmission} objects.
 * This extends the basic ListModel contract as follows:
 * all ListDataEvents sent to ListDataListeners will have their 
 * <code>source</code> set to the {@link Transmission} object concerned, 
 * and will have both <code>index</code> values equal to each other.
 *
 * @author   Mark Taylor
 * @since    24 Nov 2008
 */
class TransmissionListModel extends AbstractListModel {
    private final List list_;
    private final ChangeListener changeListener_;
    private int removeDelay_;

    /**
     * Constructor.
     *
     * @param   removeDelay  delay in milliseconds after message completion
     *                       before transmission is removed from list
     */
    public TransmissionListModel( int removeDelay ) {
        removeDelay_ = removeDelay;
        list_ = new ArrayList();
        changeListener_ = new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                Object src = evt.getSource();
                assert src instanceof Transmission;
                if ( src instanceof Transmission ) {
                    transmissionChanged( (Transmission) src );
                }
            }
        };
    }

    /**
     * Called whenever a transmission which is in this list has changed
     * state.
     *
     * @param   trans  transmission
     */
    private void transmissionChanged( final Transmission trans ) {
        int index = list_.indexOf( trans );
        if ( index >= 0 ) {
            fireContentsChanged( trans, index, index );
            if ( trans.isDone() && removeDelay_ >= 0 ) {
                long sinceDone =
                    System.currentTimeMillis() - trans.getDoneTime();
                long delay = removeDelay_ - sinceDone;
                if ( delay <= 0 ) {
                    removeTransmission( trans );
                }
                else {
                    ActionListener remover = new ActionListener() {
                        public void actionPerformed( ActionEvent evt ) {
                            removeTransmission( trans );
                        }
                    };
                    new Timer( (int) delay + 1, remover ).start();
                }
            }
        }
    }

    public int getSize() {
        return list_.size();
    }

    public Object getElementAt( int index ) {
        return list_.get( index );
    }

    /**
     * Adds a transmission to this list.
     *
     * @param  trans  transmission to add
     */
    public void addTransmission( Transmission trans ) {
        int index = list_.size();
        list_.add( trans );
        fireIntervalAdded( trans, index, index );
        trans.addChangeListener( changeListener_ );
    }

    /**
     * Removes a transmission from this list.
     *
     * @param  trans  transmission to remove
     */
    public void removeTransmission( final Transmission trans ) {
        int index = list_.indexOf( trans );
        if ( index >= 0 ) {
            list_.remove( index );
            fireIntervalRemoved( trans, index, index );
        }

        // Defer listener removal to avoid concurrency problems 
        // (trying to remove a listener which generated this event).
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                trans.removeChangeListener( changeListener_ );
            }
        } );
    }
}
