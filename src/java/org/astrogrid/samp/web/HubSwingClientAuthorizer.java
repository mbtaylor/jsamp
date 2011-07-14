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
     */
    public HubSwingClientAuthorizer( Component parent ) {
        super( parent );
    }

    public boolean authorize( HttpServer.Request request, String appName ) {
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
        lineList.add( "You should only accept if you have just performed" );
        lineList.add( "some action in the browser, on a web site you trust," );
        lineList.add( "that you expect to have caused this." );
        lineList.add( "\n" );
        lineList.add( "Do you authorize connection?" );
        return getResponse( (String[]) lineList.toArray( new String[ 0 ] ) );
    }
}
