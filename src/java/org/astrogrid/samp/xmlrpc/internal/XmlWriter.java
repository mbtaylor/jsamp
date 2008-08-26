package org.astrogrid.samp.xmlrpc.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Utility class for writing XML.
 *
 * @author   Mark Taylor
 * @since    26 Aug 2008
 */
class XmlWriter {
    private final Writer out_;
    private final int indent_;
    private int iLevel_;
    private static final String ENCODING = "UTF-8";

    /**
     * Constructor.
     *
     * @param   out  destination stream
     * @param   indent  number of spaces to indent each element level
     */
    public XmlWriter( OutputStream out, int indent ) throws IOException {
        out_ = new OutputStreamWriter( out, ENCODING );
        indent_ = indent;
        literal( "<?xml version='1.0' encoding='" + ENCODING + "'?>" );
        newline();
    }

    /** 
     * Start an element.
     * 
     * @param  element  tag name
     */
    public void start( String element ) throws IOException {
        pad( iLevel_++ );
        out_.write( '<' );
        out_.write( element );
        out_.write( '>' );
        newline();
    }

    /**
     * End an element.
     *
     * @param  element  tag name
     */
    public void end( String element ) throws IOException {
        pad( --iLevel_ );
        out_.write( "</" );
        out_.write( element );
        out_.write( '>' );
        newline();
    }

    /**
     * Write an element and its text content.
     *
     * @param  element  tag name
     * @param  content  element text content
     */
    public void inline( String element, String content ) throws IOException {
        pad( iLevel_ );
        out_.write( '<' );
        out_.write( element );
        out_.write( '>' );
        text( content );
        out_.write( "</" );
        out_.write( element );
        out_.write( '>' );
        newline();
    }

    /**
     * Writes text.  Any escaping required for XML output will be
     * taken care of.
     *
     * @param   txt   text to output
     */
    public void text( String txt ) throws IOException {
        int leng = txt.length();
        for ( int i = 0; i < leng; i++ ) {
            char c = txt.charAt( i );
            switch ( c ) {
                case '&':
                    out_.write( "&amp;" );
                    break;
                case '<':
                    out_.write( "&lt;" );
                    break;
                case '>':
                    out_.write( "&gt;" );
                    break;
                default:
                    out_.write( c );
            }
        }
    }

    /**
     * Writes text with no escaping of XML special characters etc.
     *
     * @param  txt  raw text to output
     */
    public void literal( String txt ) throws IOException {
        out_.write( txt );
    }

    /**
     * Writes a new line character.
     */
    public void newline() throws IOException {
        out_.write( '\n' );
    }

    /**
     * Writes a SAMP-friendly object in XML-RPC form.
     *
     * @param  value  object to serialize; must be a string, list or map
     */
    public void sampValue( Object value ) throws IOException {
        if ( value instanceof String ) {
            inline( "value", (String) value );
        }
        else if ( value instanceof List ) {
            start( "value" );
            start( "array" );
            start( "data" );
            for ( Iterator it = ((List) value).iterator(); it.hasNext(); ) {
                sampValue( it.next() );
            }
            end( "data" );
            end( "array" );
            end( "value" );
        }
        else if ( value instanceof Map ) {
            start( "value" );
            start( "struct" );
            for ( Iterator it = ((Map) value).entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                start( "member" );
                inline( "name", entry.getKey().toString() );
                sampValue( entry.getValue() );
                end( "member" );
            }
            end( "struct" );
            end( "value" );
        }
        else {
            throw new XmlRpcFormatException( "Unsupported object type "
                                           + value.getClass().getName() );
        }
    }

    /**
     * Closes the stream.
     */
    public void close() throws IOException {
        out_.close();
    }

    /**
     * Outputs start-of-line padding for a given level of indentation.
     *
     * @param  level  level of XML element ancestry
     */
    private void pad( int level ) throws IOException {
        int npad = level * indent_;
        for ( int i = 0; i < npad; i++ ) {
            out_.write( ' ' );
        }
    }
}
