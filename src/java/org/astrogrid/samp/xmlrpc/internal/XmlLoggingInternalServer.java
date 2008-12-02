package org.astrogrid.samp.xmlrpc.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Freestanding InternalServer implementation which logs all incoming
 * and outgoing HTTP data.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */
public class XmlLoggingInternalServer extends InternalServer {

    private final PrintStream out_;

    /**
     * Constructor based on a given HTTP server.
     * It is the caller's responsibility to configure and start the HttpServer.
     *
     * @param  httpServer  server for processing HTTP requests
     * @param  path   path part of server endpoint (starts with "/");
     * @param  out   output stream for loggging
     */
    public XmlLoggingInternalServer( HttpServer server, String path,
                                     PrintStream out )
            throws IOException {
        super( server, path );
        out_ = out;
    }

    /**
     * Constructs a server running with default characteristics
     * on any free port.  The server is started as a daemon thread.
     *
     * @param  out   output stream for loggging
     */
    public XmlLoggingInternalServer( PrintStream out ) throws IOException {
        super();
        out_ = out;
    }

    protected HttpServer.Response getXmlRpcResponse( byte[] body ) {
        synchronized ( out_ ) {
            out_.println( "SERVER IN:" );
            try {
                out_.write( body );
            }
            catch ( IOException e ) {
            }
            out_.println();
        }
        return new LoggingResponse( super.getXmlRpcResponse( body ) );
    }

    private class LoggingResponse extends HttpServer.Response {
        final HttpServer.Response base_;
        LoggingResponse( HttpServer.Response base ) {
            super( base.getStatusCode(), base.getStatusPhrase(),
                   base.getHeaderMap() );
            base_ = base;
        }
        protected void writeBody( OutputStream out ) throws IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            base_.writeBody( bout );
            byte[] bbuf = bout.toByteArray();
            synchronized ( out_ ) {
                out_.println( "SERVER OUT:" );
                out_.write( bbuf );
                out_.println();
            }
            out.write( bbuf );
        }
    }
}
