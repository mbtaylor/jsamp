package org.astrogrid.samp.gui;

import java.awt.BorderLayout;
import javax.swing.JToggleButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.client.HubConnector;

public class HubMonitor extends JPanel {

    private final HubConnector connector_;

    public HubMonitor( int autoSec ) throws SampException {
        super( new BorderLayout() );
        connector_ = new HubConnector();
        connector_.setAutoconnect( autoSec );
        connector_.declareSubscriptions( connector_.computeSubscriptions() );
        Metadata meta = new Metadata();
        meta.setName( "HubMonitor" );
        meta.setDescriptionText( "GUI hub monitor utility" );
        meta.put( "author", "Mark Taylor" );
        connector_.declareMetadata( meta );
        HubView hubView = new HubView();
        hubView.setClientListModel( connector_.getClientListModel() );
        add( hubView, BorderLayout.CENTER );
        add( new JToggleButton( connector_.getRegisterAction() ),
             BorderLayout.SOUTH );
        connector_.setActive( true );
    }

    public static int runMain( String[] args ) throws SampException {
        JFrame frame = new JFrame();
        frame.getContentPane().add( new HubMonitor( 5 ) );
        frame.pack();
        frame.setVisible( true );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        return 0;
    }

    public static void main( String[] args ) throws SampException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( 1 );
        }
    }
}