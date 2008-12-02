package org.astrogrid.samp.xmlrpc.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.net.URL;

/**
 * InternalClient subclass which additionally logs all XML-RPC calls/responses
 * to an output stream.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */         
public class XmlLoggingInternalClient extends InternalClient {

    private final PrintStream out_;

    /**
     * Constructor.
     *
     * @param  endpoint  endpoint
     * @param  out  output stream for logging
     */
    public XmlLoggingInternalClient( URL endpoint, PrintStream out ) {
        super( endpoint );
        out_ = out;
    }

    protected byte[] serializeCall( String method, List paramList )
            throws IOException {
        byte[] buf = super.serializeCall( method, paramList );
        synchronized ( out_ ) {
            out_.println( "CLIENT OUT:" );
            out_.write( buf );
            out_.println();
        }
        return buf;
    }

    protected Object deserializeResponse( InputStream in )
            throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[ 1024 ];
        for ( int nb; ( nb = in.read( buf ) ) >= 0; ) {
            out_.write( buf, 0, nb );
            bout.write( buf, 0, nb );
        }
        synchronized ( out_ ) {
            out_.println( "CLIENT IN:" );
            out_.write( buf );
            out_.println();
        }
        InputStream copyIn = new ByteArrayInputStream( bout.toByteArray() );
        return super.deserializeResponse( copyIn );
    }
}
