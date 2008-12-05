package org.astrogrid.samp.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.Timer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.astrogrid.samp.SampUtils;

/**
 * TableModel implementation which displays Transmission objects.
 * To populate it, either call {@link #addTransmission} directly or 
 * add it as a listener to one or more TransmissionListModels.
 *
 * @author   Mark Taylor
 * @since    5 Dec 2008
 */
class TransmissionTableModel implements TableModel, ListDataListener {

    private final int removeDelay_;
    private final List transList_;
    private final List tableListenerList_;
    private final Column[] columns_;

    /**
     * Constructor.
     *
     * @param   showSender  true if a Sender column is required
     * @param   showReceiver  true if a Receiver column is required
     * @param   removeDelay  time in milliseconds after transmission resolution
     *          that it will stay in the table - after this it will be
     *          removed automatically
     */
    public TransmissionTableModel( final boolean showSender,
                                   final boolean showReceiver,
                                   int removeDelay ) {
        removeDelay_ = removeDelay;
        transList_ = new ArrayList();
        tableListenerList_ = new ArrayList();

        // Set up table columns.
        List colList = new ArrayList();
        colList.add( new Column( "MType", String.class ) {
            Object getValue( Transmission trans ) {
                return trans.getMessage().getMType();
            }
        } );
        if ( showSender ) {
            colList.add( new Column( "Sender", String.class ) {
                Object getValue( Transmission trans ) {
                    return trans.getSender();
                }
            } );
        }
        if ( showReceiver ) {
            colList.add( new Column( "Receiver", String.class ) {
                Object getValue( Transmission trans ) {
                    return trans.getReceiver();
                }
            } );
        }
        colList.add( new Column( "Status", String.class ) {
            Object getValue( Transmission trans ) {
                if ( trans.isDone() ) {
                    return trans.getStatus();
                }
                else {
                    return showReceiver ? "...waiting..."
                                        : "...processing...";
                }
            }
        } );
        columns_ = (Column[]) colList.toArray( new Column[ 0 ] );
    }

    /**
     * Returns the transmission corresponding to a given table row.
     *
     * @param  irow   row index
     * @param   transmission displayed in row irow
     */
    public Transmission getTransmission( int irow ) {
        return (Transmission) transList_.get( irow );
    }

    /**
     * Adds a transmission (row) to this model.  It will appear at the top.
     *
     * @param  trans   transmission to add
     */
    public void addTransmission( Transmission trans ) {
        
        transList_.add( 0, trans );
        fireTableChanged( new TableModelEvent( this, 0, 0,
                                               TableModelEvent.ALL_COLUMNS,
                                               TableModelEvent.INSERT ) );
    }

    /**
     * Removes a transmission from this model.
     *
     * @param  trans  transmission to remove
     */
    public void removeTransmission( Transmission trans ) {
        int index = transList_.indexOf( trans );
        if ( index >= 0 ) {
            transList_.remove( index );
            fireTableChanged( new TableModelEvent( this, index, index,
                                                   TableModelEvent.ALL_COLUMNS,
                                                   TableModelEvent.DELETE ) );
        }
    }

    public int getColumnCount() {
        return columns_.length;
    }

    public int getRowCount() {
        return transList_.size();
    }

    public Object getValueAt( int irow, int icol ) {
        return columns_[ icol ].getValue( getTransmission( irow ) );
    }

    public String getColumnName( int icol ) {
        return columns_[ icol ].name_;
    }

    public Class getColumnClass( int icol ) {
        return columns_[ icol ].clazz_;
    }

    public void contentsChanged( ListDataEvent evt ) {
        Object src = evt.getSource();
        assert src instanceof Transmission;
        assert evt.getIndex0() == evt.getIndex1();
        int index = transList_.indexOf( src );
        if ( index >= 0 ) {
            final Transmission trans = (Transmission) src;
            if ( trans.isDone() ) {
                if ( removeDelay_ >= 0 ) {
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
                        new Timer( (int) (delay + 1), remover ).start();
                    }
                }
                else {
                    fireTableChanged( new TableModelEvent( this, index ) );
                }
            }
            else {
                fireTableChanged( new TableModelEvent( this, index ) );
            }
        }
    }

    public void intervalAdded( ListDataEvent evt ) {
        Object src = evt.getSource();
        assert src instanceof Transmission;
        assert evt.getIndex0() == evt.getIndex1();
        if ( src instanceof Transmission ) {
            addTransmission( (Transmission) src );
        }
    }

    public void intervalRemoved( ListDataEvent evt ) {
        Object src = evt.getSource();
        assert src instanceof Transmission;
        assert evt.getIndex0() == evt.getIndex1();
        // no action required - we remove our own elements
    }

    public boolean isCellEditable( int irow, int icol ) {
        return false;
    }

    public void setValueAt( Object value, int irow, int icol ) {
        throw new UnsupportedOperationException();
    }

    public void addTableModelListener( TableModelListener listener ) {
        tableListenerList_.add( listener );
    }

    public void removeTableModelListener( TableModelListener listener ) {
        tableListenerList_.remove( listener );
    }

    private void fireTableChanged( TableModelEvent evt ) {
        for ( Iterator it = tableListenerList_.iterator(); it.hasNext(); ) {
            ((TableModelListener) it.next()).tableChanged( evt );
        }
    }

    /**
     * Describes metadata and data for a table column.
     */
    private abstract class Column {
        final String name_;
        final Class clazz_;

        /**
         * Constructor.
         *
         * @param  name  column name
         * @param  clazz  column content class
         */
        Column( String name, Class clazz ) {
            name_ = name;
            clazz_ = clazz;
        }

        /**
         * Returns the item in this column for a given transmission.
         *
         * @param   trans  transmission
         * @return   cell value
         */
        abstract Object getValue( Transmission trans );
    }
}
