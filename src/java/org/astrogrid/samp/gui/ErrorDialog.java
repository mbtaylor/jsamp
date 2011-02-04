package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.StringWriter;
import java.io.PrintWriter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Dialog window which displays an error message, possibly with some 
 * verbose details optionally visible.
 *
 * @author   Mark Taylor
 * @since    5 Sep 2008
 */
public abstract class ErrorDialog extends JDialog {

    /**
     * Constructor.
     *
     * @param   owner  parent frame
     * @param   title   dialog title
     * @param   summary  short text string describing what's up
     */
    protected ErrorDialog( Frame owner, String title, String summary ) {
        super( owner, title == null ? "Error" : title, true );
        setDefaultCloseOperation( DISPOSE_ON_CLOSE );

        // Define buttons.
        final JPanel main = new JPanel( new BorderLayout() );
        JButton disposeButton = new JButton();
        JButton detailButton = new JButton();
        final JComponent dataBox = new JPanel( new BorderLayout() );
        dataBox.add( new JLabel( summary ) );
        JComponent buttonBox = Box.createHorizontalBox();
 
        // Populate main panel.
        Border gapBorder = BorderFactory.createEmptyBorder( 5, 5, 5, 5 );
        JComponent iconLabel =
            new JLabel( UIManager.getIcon( "OptionPane.errorIcon" ) );
        iconLabel.setBorder( gapBorder );
        main.add( iconLabel, BorderLayout.WEST );
        buttonBox.add( Box.createHorizontalGlue() );
        buttonBox.add( disposeButton );
        buttonBox.add( Box.createHorizontalStrut( 10 ) );
        buttonBox.add( detailButton );
        buttonBox.add( Box.createHorizontalGlue() );
        dataBox.setBorder( gapBorder );
        main.add( buttonBox, BorderLayout.SOUTH );
        main.add( dataBox, BorderLayout.CENTER );
        main.setBorder( gapBorder );

        // Set button action for dismiss button.
        disposeButton.setAction( new AbstractAction( "OK" ) {
            public void actionPerformed( ActionEvent evt ) {
                dispose();
            }
        } );

        // Set button action for display detail button.
        detailButton.setAction( new AbstractAction( "Show Details" ) {
            public void actionPerformed( ActionEvent evt ) {
                JTextArea ta = new JTextArea();
                ta.setLineWrap( false );
                ta.setEditable( false );
                ta.append( getDetailText() );
                ta.setCaretPosition( 0 );
                JScrollPane scroller = new JScrollPane( ta );
                dataBox.removeAll();
                dataBox.add( scroller );
                Dimension size = dataBox.getPreferredSize();
                size.height = Math.min( size.height, 300 );
                size.width = Math.min( size.width, 500 );
                dataBox.revalidate();
                dataBox.setPreferredSize( size );
                pack();
                setEnabled( false );
            }
        } );
        getContentPane().add( main );
    }

    /**
     * Supplies the text to be displayed in the detail panel.
     *
     * @return  detail text
     */
    protected abstract String getDetailText();

    /**
     * Pops up a window which shows the content of a exception.
     *
     * @param   parent  parent component
     * @param   title   window title
     * @param   summary  short text string
     * @param   error   throwable
     */
    public static void showError( Component parent, String title,
                                  String summary, final Throwable error ) {
        Frame fparent = parent == null
                      ? null
                      : (Frame) SwingUtilities
                               .getAncestorOfClass( Frame.class, parent );

        JDialog dialog = new ErrorDialog( fparent, title, summary ) {
            protected String getDetailText() {
                StringWriter traceWriter = new StringWriter();
                error.printStackTrace( new PrintWriter( traceWriter ) );
                return traceWriter.toString();
            }
        };
        dialog.setLocationRelativeTo( parent );
        dialog.pack();
        dialog.setVisible( true );
    }
}
