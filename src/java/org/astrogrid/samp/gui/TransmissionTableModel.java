package org.astrogrid.samp.gui;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.astrogrid.samp.Client;

/**
 * TableModel implementation which displays Transmission objects.
 *
 * @author   Mark Taylor
 * @since    5 Dec 2008
 */
class TransmissionTableModel implements TableModel {

    private final List transList_;
    private final List tableListenerList_;
    private final ChangeListener changeListener_;
    private final Column[] columns_;
    private int maxRows_;
    private int removeDelay_;

    /** Cell renderer for Transmission.Status objects. */
    public static final TableCellRenderer STATUS_RENDERER =
        createStatusCellRenderer();

    /** Cell renderer for Client objects. */
    private static final TableCellRenderer CLIENT_RENDERER =
        createClientCellRenderer();

    /**
     * Constructor.
     *
     * @param   showSender  true if a Sender column is required
     * @param   showReceiver  true if a Receiver column is required
     * @param   removeDelay  time in milliseconds after transmission resolution
     *          that it will stay in the table - after this it will be
     *          removed automatically
     * @param   maxRows  maximum row count for table - if not set to a finite
     *          value, Swing can get overloaded in very high message traffic
     */
    public TransmissionTableModel( final boolean showSender,
                                   final boolean showReceiver,
                                   int removeDelay, int maxRows ) {
        removeDelay_ = removeDelay;
        maxRows_ = maxRows;
        transList_ = new LinkedList();
        tableListenerList_ = new ArrayList();

        // Set up table columns.
        List colList = new ArrayList();
        int charWidth = 8;
        int icol = 0;
        colList.add( new Column( "MType", String.class,
                                 new TableColumn( icol++, 30 * charWidth ) ) {
            Object getValue( Transmission trans ) {
                return trans.getMessage().getMType();
            }
        } );
        if ( showSender ) {
            colList.add( new Column( "Sender", Object.class,
                                     new TableColumn( icol++, 20 * charWidth,
                                                      CLIENT_RENDERER,
                                                      null ) ) {
                Object getValue( Transmission trans ) {
                    return trans.getSender();
                }
            } );
        }
        if ( showReceiver ) {
            colList.add( new Column( "Receiver", Object.class,
                                     new TableColumn( icol++, 20 * charWidth,
                                                      CLIENT_RENDERER,
                                                      null ) ) {
                Object getValue( Transmission trans ) {
                    return trans.getReceiver();
                }
            } );
        }
        colList.add( new Column( "Status", Object.class,
                                 new TableColumn( icol++, 16 * charWidth,
                                                  STATUS_RENDERER, null ) ) {
            Object getValue( Transmission trans ) {
                return trans.getStatus();
            }
        } );
        columns_ = (Column[]) colList.toArray( new Column[ 0 ] );

        // Set up listener to monitor changes of transmissions.
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
        while ( transList_.size() > maxRows_ ) {
            transList_.remove( maxRows_ );
            fireTableChanged( new TableModelEvent( this, maxRows_, maxRows_,
                                                   TableModelEvent.ALL_COLUMNS,
                                                   TableModelEvent.DELETE ) );
        }
        transList_.add( 0, trans );
        trans.addChangeListener( changeListener_ );
        fireTableChanged( new TableModelEvent( this, 0, 0,
                                               TableModelEvent.ALL_COLUMNS,
                                               TableModelEvent.INSERT ) );
    }

    /**
     * Removes a transmission from this model.
     *
     * @param  trans  transmission to remove
     */
    public void removeTransmission( final Transmission trans ) {
        int index = transList_.indexOf( trans );
        if ( index >= 0 ) {
            transList_.remove( index );
            fireTableChanged( new TableModelEvent( this, index, index,
                                                   TableModelEvent.ALL_COLUMNS,
                                                   TableModelEvent.DELETE ) );
        }

        // Defer listener removal to avoid concurrency problems
        // (trying to remove a listener which generated this event).
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                trans.removeChangeListener( changeListener_ );
            }
        } );
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

    /** 
     * Returns a TableColumn suitable for a given column of this table.
     * Can be used for more customised presentation.
     *
     * @param   icol   column index
     * @return   table column
     */
    public TableColumn getTableColumn( int icol ) {
        return columns_[ icol ].tcol_;
    }

    /**
     * Called whenever a transmission which is in this list has changed
     * state.
     *
     * @param   trans  transmission
     */
    private void transmissionChanged( final Transmission trans ) {
        int index = transList_.indexOf( trans );
        if ( index >= 0 ) {
            fireTableChanged( new TableModelEvent( this, index ) );
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

    /**
     * Passes a table event to all registered listeners.
     *
     * @param  evt   event to forward
     */
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
        final TableColumn tcol_;

        /**
         * Constructor.
         *
         * @param  name  column name
         * @param  clazz  column content class
         */
        Column( String name, Class clazz, TableColumn tcol ) {
            name_ = name;
            clazz_ = clazz;
            tcol_ = tcol;
            tcol_.setHeaderValue( name );
        }

        /**
         * Returns the item in this column for a given transmission.
         *
         * @param   trans  transmission
         * @return   cell value
         */
        abstract Object getValue( Transmission trans );
    }

    /**
     * Template custom TableCellRenderer for subclassing.
     */
    private static abstract class CustomTableCellRenderer
                                  extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent( JTable table,
                                                        Object value,
                                                        boolean isSel,
                                                        boolean hasFocus,
                                                        int irow, int icol ) {
            int size;
            try {
                size =
                    (int) Math.ceil( getFont()
                                    .getMaxCharBounds( ((Graphics2D)
                                                        table.getGraphics())
                                                      .getFontRenderContext() )
                                    .getHeight() );
            }
            catch ( NullPointerException e ) {
                size = 16;
            }
            Component comp =
                super.getTableCellRendererComponent( table, value, isSel,
                                                     hasFocus, irow, icol );
            if ( comp instanceof JLabel ) {
                configureLabel( (JLabel) comp, value, size - 2 );
            }
            return comp;
        }

        /**
         * Configures a JLabel given the value to render and the 
         * component size.
         *
         * @param   label   renderer component to configure
         * @param   value   object to render
         * @param   height  component height in pixels
         */
        abstract void configureLabel( JLabel label, Object value, int height );
    }

    /**
     * Returns a cell renderer for Transmission.Status objects.
     *
     * @return  table cell renderer
     */
    private static TableCellRenderer createStatusCellRenderer() {
        return new CustomTableCellRenderer() {
            void configureLabel( JLabel label, Object value, int height ) {
                if ( value instanceof Transmission.Status ) {
                    Transmission.Status status = (Transmission.Status) value;
                    label.setText( status.getText() );
                    label.setIcon( status.getIcon( height ) );
                }
            }
        };
    }

    /**
     * Returns a cell renderer for Client objects.
     *
     * @return  table cell renderer
     */
    private static TableCellRenderer createClientCellRenderer() {
        final IconStore iconStore =
            new IconStore( IconStore.createEmptyIcon( 16 ) );
        return new CustomTableCellRenderer() {
            void configureLabel( JLabel label, Object value, int height ) {
                if ( value instanceof Client ) {
                    Client client = (Client) value;
                    label.setText( client.toString() );
                    label.setIcon( ClientListCellRenderer
                                  .reshapeIcon( iconStore.getIcon( client ),
                                   height ) );
                }
            }
        };
    }
}
