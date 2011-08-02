package org.astrogrid.samp.web;

import java.net.ServerSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

/**
 * CorsHttpServer subclass which performs logging to a given print stream
 * at the HTTP level.  Logging is not done through the logging system.
 *
 * @author   Mark Taylor
 * @since    2 Feb 2011
 */
public class LoggingCorsHttpServer extends CorsHttpServer {

    private final PrintStream out_;
    private int iSeq_;

    /**
     * Constructor.
     *
     * @param  socket  socket hosting the service
     * @param  auth   defines which domains requests will be permitted from
     * @param  out  destination stream for logging
     */
    public LoggingCorsHttpServer( ServerSocket socket, OriginAuthorizer auth,
                                  PrintStream out ) throws IOException {
        super( socket, auth );
        out_ = out;
    }

    public Response serve( Request request ) {
        int iseq;
        synchronized ( this ) {
            iseq = ++iSeq_;
        }
        logRequest( request, iseq );
        return new LoggedResponse( super.serve( request ), iseq,
                                   "POST".equals( request.getMethod() ) );
    }

    /**
     * Logs a given request.
     *
     * @param  request  HTTP request
     * @param   iseq   index of the request; unique integer for each request
     */
    private void logRequest( Request request, int iseq ) {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( '\n' );
        appendBanner( sbuf, '>', iseq );
        sbuf.append( request.getMethod() )
            .append( ' ' )
            .append( request.getUrl() )
            .append( '\n' );
        appendHeaders( sbuf, request.getHeaderMap() );
        byte[] body = request.getBody();
        if ( body != null && body.length > 0 ) {
            sbuf.append( '\n' );
            try {
                sbuf.append( new String( request.getBody(), "utf-8" ) );
            }
            catch ( UnsupportedEncodingException e ) {
                throw new AssertionError( "No utf-8??" );
            }
        }
        out_.println( sbuf );
    }

    /**
     * Adds a line to the given stringbuffer which indicates information
     * relating to a given sequence number follows.
     * 
     * @param  sbuf  string buffer to add to
     * @param  c   filler character
     * @param  iseq  sequence number
     */
    private void appendBanner( StringBuffer sbuf, char c, int iseq ) {
        String label = Integer.toString( iseq );
        int nc = 75 - label.length();
        for ( int i = 0; i < nc; i++ ) {
            sbuf.append( c );
        }
        sbuf.append( ' ' )
            .append( label )
            .append( '\n' );
    }

    /**
     * Adds HTTP header information to a string buffer.
     *
     * @param  sbuf  buffer to add lines to
     * @param  map   header key->value pair map
     */
    private void appendHeaders( StringBuffer sbuf, Map map ) {
        for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            sbuf.append( entry.getKey() )
                .append( ": " )
                .append( entry.getValue() )
                .append( '\n' );
        }
    }

    /**
     * HTTP response which will log its content at an appropriate time.
     */
    private class LoggedResponse extends Response {
        private final Response base_;
        private final boolean logBody_;
        private final String headText_;
        private String bodyText_;

        /**
         * Constructor.
         *
         * @param   base   response on which this one is based
         * @param   iseq   sequence number of request that this is a response to
         * @param   logBody  true iff the body of the response is to be logged
         */
        LoggedResponse( Response base, int iseq, boolean logBody ) {
            super( base.getStatusCode(), base.getStatusPhrase(),
                   base.getHeaderMap() );
            base_ = base;
            logBody_ = logBody;
            StringBuffer sbuf = new StringBuffer();
            sbuf.append( '\n' );
            appendBanner( sbuf, '<', iseq );
            sbuf.append( getStatusCode() )
                .append( ' ' )
                .append( getStatusPhrase() )
                .append( '\n' );
            appendHeaders( sbuf, getHeaderMap() );
            headText_ = sbuf.toString();
        }

        public void writeBody( final OutputStream out ) throws IOException {

            // This method captures the logging output as well as writing
            // the body text to the given stream.  The purpose of doing it
            // like this rather than writing the logging information
            // directly is so that this method can be harmlessly called
            // multiple times (doesn't normally happen, but can do sometimes).

            // Prepare an object (an OutputStream) to which you can write
            // the body content and then use its toString method to get
            // the loggable text.  This loggable text is either the
            // body content itself, or an indication of how many bytes it
            // contained, depending on the logBody_ flag.
            final OutputStream lout = logBody_
                ? (OutputStream) new ByteArrayOutputStream() {
                      public String toString() {
                          String txt;
                          try {
                              txt = new String( buf, 0, count, "utf-8" );
                          }
                          catch ( UnsupportedEncodingException e ) {
                              txt = e.toString();
                          }
                          return "\n" + txt + "\n";
                      }
                  }
                : (OutputStream) new CountOutputStream() {
                      public String toString() {
                          return count_ > 0 
                               ? "<" + count_ + " bytes of output omitted>\n"
                               : "";
                      }
                  };

            // Prepare an output stream which writes both to the normal
            // response destination and to the content logging object we've
            // just set up.
            OutputStream teeOut = new OutputStream() {
                public void write( byte[] b ) throws IOException {
                    lout.write( b );
                    out.write( b );
                }
                public void write( byte[] b, int off, int len )
                        throws IOException {
                    lout.write( b, off, len );
                    out.write( b, off, len );
                }
                public void write( int b ) throws IOException {
                    lout.write( b );
                    out.write( b );
                }
            };

            // Write the body content to the response output stream,
            // and store the loggable output.
            String slog;
            try {
                base_.writeBody( teeOut );
                slog = lout.toString();
            }
            catch ( IOException e ) {
                slog = "log error? " + e + "\n";
            }
            bodyText_ = slog;
        }

        public void writeResponse( final OutputStream out ) throws IOException {
            super.writeResponse( out );
            out_.print( headText_ + bodyText_ );
        }
    }

    /**
     * OutputStream subclass which counts the number of bytes it is being
     * asked to write, but otherwise does nothing.
     */
    private static class CountOutputStream extends OutputStream {
        long count_;  // number of bytes counted so far
        public void write( byte[] b ) {
            count_ += b.length;
        }
        public void write( byte[] b, int off, int len ) {
            count_ += len;
        }
        public void write( int b ) {
            count_++;
        }
    }
}
