package org.astrogrid.samp.web;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * ClientAuthorizer implementation used by the Web Profile Hub to
 * enquire from the user about authorization of clients applying to register.
 * It is like its superclass {@link SwingClientAuthorizer}, but
 * modifies the text slightly.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class HubSwingClientAuthorizer extends SwingClientAuthorizer {

    /** 
     * Constructor.
     *
     * @param  parent  parent component
     * @param   permitRemote  true iff clients from non-local hosts can be
     *          authorized; if false, they will be rejected without asking
     *          the user; set false only with care
     */
    public HubSwingClientAuthorizer( Component parent, boolean permitRemote ) {
        super( parent, permitRemote );
    }

    public boolean authorize( HttpServer.Request request, String appName ) {
        if ( ! checkAddress( request, appName ) ) {
            return false;
        }
        List lineList = new ArrayList();
        lineList.add( "The following application, "
                    + "probably running in a browser," );
        lineList.add( "is requesting SAMP Hub registration:" );
        lineList.add( "\n" );
        lineList.addAll( Arrays.asList( applicantLines( request, appName ) ) );
        lineList.add( "\n" );
        lineList.add( "If you permit this, it will have most "
                    + "of the privileges" );
        lineList.add( "of user " + System.getProperty( "user.name" )
                    + ", such as file read/write." );
        lineList.add( "\n" );
        lineList.add( "Do you authorize connection?" );
        return getResponse( (String[]) lineList.toArray( new String[ 0 ] ) );
    }
}
