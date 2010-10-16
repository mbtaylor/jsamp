package org.astrogrid.samp.xmlrpc.apache;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcHandler;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.xmlrpc.SampXmlRpcServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcHandler;

/**
 * SampXmlRpcServer implementation based on Apache XML-RPC library.
 *
 * @author   Mark Taylor
 * @since    22 Aug 2008
 */
public class ApacheServer implements SampXmlRpcServer {

    private final WebServer webServer_;
    private final URL endpoint_;
    private final List handlerList_;

    /**
     * Private constructor used by all other constructors.
     * Uses the private LabelledServer class to aggregate the required 
     * information.
     *
     * @param  server  server with metadata
     */
    private ApacheServer( LabelledServer server ) {
        webServer_ = server.webServer_;
        endpoint_ = server.endpoint_;
        handlerList_ = Collections.synchronizedList( new ArrayList() );
        webServer_.addHandler( "$default", new XmlRpcHandler() {
            public Object execute( String method, Vector params )
                    throws Exception {
                return doExecute( method, params );
            }
        } );
    }

    /**
     * Constructs a new server based on a given WebServer object.
     * Responsibility for <code>start</code>ing the WebServer and performing
     * any other required configuration lies with the caller.
     *
     * @param  webServer  apache xmlrpc webserver object
     * @param  port       port number on which the server is running
     */
    public ApacheServer( WebServer webServer, int port ) {
        this( new LabelledServer( webServer, getServerEndpoint( port ) ) );
    }

    /**
     * Constructs a new server starting up a new WebServer object.
     * The server runs in a daemon thread.
     */
    public ApacheServer() throws IOException {
        this( createLabelledServer( true ) );
        webServer_.start();
    }

    public URL getEndpoint() {
        return endpoint_;
    }

    public void addHandler( SampXmlRpcHandler handler ) {
        handlerList_.add( handler );
    }

    public void removeHandler( SampXmlRpcHandler handler ) {
        handlerList_.remove( handler );
    }

    /**
     * Does the work for executing an XML-RPC request.
     *
     * @param   fqMethod  fully qualified XML-RPC method name
     * @param   paramVec  Apache-style list of method parameters
     */
    private Object doExecute( String fqMethod, Vector paramVec )
            throws Exception {
        SampXmlRpcHandler[] handlers =
            (SampXmlRpcHandler[])
            handlerList_.toArray( new SampXmlRpcHandler[ 0 ] );
        for ( int ih = 0; ih < handlers.length; ih++ ) {
            SampXmlRpcHandler handler = handlers[ ih ];
            if ( handler.canHandleCall( fqMethod ) ) {
                List paramList = (List) ApacheUtils.fromApache( paramVec );
                Object result = handler.handleCall( fqMethod, paramList );
                return ApacheUtils.toApache( result );
            }
        }
        throw new UnsupportedOperationException( "No handler for method "
                                               + fqMethod );
    }

    /**
     * Constructs a new LabelledServer object suitable for use with this
     * server.
     *
     * @param  isDaemon   whether the WebServer's main thread should run
     *                    in daemon mode
     */
    private static LabelledServer createLabelledServer( final boolean isDaemon )
            throws IOException {
        int port = SampUtils.getUnusedPort( 2300 );
        WebServer server = new WebServer( port ) {

            // Same as superclass implementation except that the listener
            // thread is marked as a daemon.
            public void start() {
                if ( this.listener == null ) {
                    this.listener =
                        new Thread( this, "XML-RPC Weblistener" );
                    this.listener.setDaemon( isDaemon );
                    this.listener.start();
                }
            }
        };
        return new LabelledServer( server, getServerEndpoint( port ) );
    }

    /**
     * Returns the endpoint URL to use for an Apache server running on a
     * given port.
     *
     * @param  port  port number
     * @return  URL
     */
    private static URL getServerEndpoint( int port ) {
        String endpoint =
            "http://" + SampUtils.getLocalhost() + ":" + port + "/";
        try {
            return new URL( endpoint );
        }
        catch ( MalformedURLException e ) {
            throw (Error)
                  new AssertionError( "Bad protocol http?? " + endpoint )
                 .initCause( e );
        }
    }

    /**
     * Convenience class which aggregates a WebServer and an endpoint.
     */
    private static class LabelledServer {
        private final WebServer webServer_;
        private final URL endpoint_;

        /**
         * Constructor.
         *
         * @param  webServer  web server
         * @param  endpoint   URL
         */
        LabelledServer( WebServer webServer, URL endpoint ) {
            webServer_ = webServer;
            endpoint_ = endpoint;
        }
    }
}
