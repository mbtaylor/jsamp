package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.SampUtils;

/**
 * InternalClient subclass which additionally logs all XML-RPC calls/responses
 * to an output stream.
 *
 * @author   Mark Taylor
 * @since    2 Dec 2008
 */
public class RpcLoggingInternalClient extends InternalClient {

    private final PrintStream out_;

    /**
     * Constructor.
     *
     * @param  endpoint  endpoint
     * @param  out  logging output stream
     */
    public RpcLoggingInternalClient( URL endpoint, PrintStream out ) {
        super( endpoint );
        out_ = out;
    }

    protected byte[] serializeCall( String method, List paramList )
            throws IOException {
        String paramString = SampUtils.formatObject( paramList, 2 );
        synchronized ( out_ ) {
            out_.println( "CLIENT OUT:" );
            out_.println( method );
            out_.println( paramString );
            out_.println();
        }
        return super.serializeCall( method, paramList );
    }

    protected Object deserializeResponse( InputStream in )
            throws IOException {
        Object response = super.deserializeResponse( in );
        if ( response == null ||
             response instanceof String && ((String) response).length() == 0 || 
             response instanceof Map && ((Map) response).isEmpty() ||
             response instanceof List && ((List) response).isEmpty() ) {
            // treat as no response
        }
        else {
            String responseString = SampUtils.formatObject( response, 2 );
            synchronized ( out_ ) {
                out_.println( "CLIENT IN:" );
                out_.println( responseString );
                out_.println();
            }
        }
        return response;
    }
}
