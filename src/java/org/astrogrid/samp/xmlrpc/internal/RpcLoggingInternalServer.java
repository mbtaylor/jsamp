package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.HttpServer;
import org.astrogrid.samp.xmlrpc.SampXmlRpcHandler;

/**
 * InternalServer subclass which additionally logs all XML-RPC calls/responses
 * to an output stream.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */
public class RpcLoggingInternalServer extends InternalServer {

    private final PrintStream out_;

    /**
     * Constructor based on a given HTTP server.
     * It is the caller's responsibility to configure and start the HttpServer.
     *
     * @param  server  server for processing HTTP requests
     * @param  path   path part of server endpoint (starts with "/");
     * @param  out   output stream for logging
     */
    public RpcLoggingInternalServer( HttpServer server, String path,
                                     PrintStream out )
            throws IOException {
        super( server, path );
        out_ = out;
    }

    /**
     * Constructs a server running with default characteristics
     * on any free port.  The server is started as a daemon thread.
     *
     * @param  out   output stream for logging
     */
    public RpcLoggingInternalServer( PrintStream out ) throws IOException {
        super();
        out_ = out;
    }

    protected Object handleCall( SampXmlRpcHandler handler, String methodName,
                                 List paramList ) throws Exception {
        String paramString = SampUtils.formatObject( paramList, 2 );
        synchronized ( out_ ) {
            out_.println( "SERVER IN:" );
            out_.println( methodName );
            out_.println( paramString );
            out_.println();
        }
        final Object result;
        try {
            result = super.handleCall( handler, methodName, paramList );
        }
        catch ( Throwable e ) {
            synchronized ( out_ ) {
                out_.println( "SERVER ERROR:" );
                out_.println( methodName );
                e.printStackTrace( out_ );
                out_.println();
            }
            if ( e instanceof Error ) {
                throw (Error) e;
            }
            else {
                throw (Exception) e;
            }
        }
        String resultString = SampUtils.formatObject( result, 2 );
        synchronized ( out_ ) {
            out_.println( "SERVER OUT:" );
            out_.println( methodName );
            out_.println( resultString );
            out_.println();
        }
        return result;
    }
}
