package org.astrogrid.samp;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Outputs a SAMP object as JSON.
 * Can do it formatted and reasonably compact.
 *
 * @author   Mark Taylor
 * @since    25 Jul 2011
 */
class JsonWriter {
    private final int indent_;
    private final String spc_;

    /**
     * Constructor with default properties.
     */
    public JsonWriter() {
        this( 2, true );
    }

    /**
     * Custom constructor.
     *
     * @param   indent  number of characters indent per level
     * @param   spacer  whether to put spaces inside brackets
     */
    public JsonWriter( int indent, boolean spacer ) {
        indent_ = indent;
        spc_ = spacer ? " " : "";
    }

    /**
     * Converts a SAMP data item to JSON.
     *
     * @param   item  SAMP-friendly object
     * @return   JSON representation
     */
    public String toJson( Object item ) {
        StringBuffer sbuf = new StringBuffer();
        toJson( sbuf, item, 0, false );
        if ( indent_ >= 0 ) {
            assert sbuf.charAt( 0 ) == '\n';
            return sbuf.substring( 1, sbuf.length() );
        }
        else {
            return sbuf.toString();
        }
    }

    /**
     * Recursive method which does the work for conversion.
     * If possible, call this method with <code>isPositioned=false</code>.
     *
     * @param   sbuf  string buffer to append result to
     * @param   item  object to convert
     * @param   level  current indentation level
     * @param  isPositioned  true if output should be direct to sbuf,
     *         false if it needs a newline plus indentation first
     */
    private void toJson( StringBuffer sbuf, Object item, int level,
                         boolean isPositioned ) {
        if ( item instanceof String ) {
            if ( ! isPositioned ) {
                sbuf.append( getIndent( level ) );
            }
            sbuf.append( '"' )
                .append( (String) item )
                .append( '"' );
        }
        else if ( item instanceof List ) {
            List list = (List) item;
            if ( list.isEmpty() ) {
                if ( ! isPositioned ) {
                    sbuf.append( getIndent( level ) );
                }
                sbuf.append( "[]" );
            }
            else {
                sbuf.append( getIntroIndent( level, '[', isPositioned ) );
                boolean isPos = ! isPositioned;
                for ( Iterator it = list.iterator(); it.hasNext(); ) {
                    toJson( sbuf, it.next(), level + 1, isPos );
                    if ( it.hasNext() ) {
                        sbuf.append( "," );
                    }
                    isPos = false;
                }
                sbuf.append( spc_ + "]" );
            }
        }
        else if ( item instanceof Map ) {
            Map map = (Map) item;
            if ( map.isEmpty() ) {
                if ( ! isPositioned ) {
                    sbuf.append( getIndent( level ) );
                }
                sbuf.append( "{}" );
            }
            else {
                sbuf.append( getIntroIndent( level, '{', isPositioned ) );
                boolean isPos = ! isPositioned;
                for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry entry = (Map.Entry) it.next();
                    Object key = entry.getKey();
                    if ( ! ( key instanceof String ) ) {
                        throw new DataException( "Non-string key in map:"
                                               + key );
                    }
                    toJson( sbuf, key, level + 1, isPos );
                    sbuf.append( ":" + spc_ );
                    toJson( sbuf, entry.getValue(), level + 1, true );
                    if ( it.hasNext() ) {
                        sbuf.append( "," );
                    }
                    isPos = false;
                }
                sbuf.append( spc_ + "}" );
            }
        }
        else {
            throw new DataException( "Illegal data type " + item );
        }
    }

    /**
     * Returns prepended whitespace containing an opener character.
     *
     * @param  level  indentation level
     * @param  chr  opener character
     * @param  isPositioned  true if output should be direct to sbuf,
     *         false if it needs a newline plus indentation first
     * @return  string to prepend
     */
    private String getIntroIndent( int level, char chr, boolean isPositioned ) {
        if ( isPositioned ) {
            return new StringBuffer().append( chr ).toString();
        }
        else {
            StringBuffer sbuf = new StringBuffer();
            sbuf.append( getIndent( level ) );
            sbuf.append( chr );
            for ( int ic = 0; ic < indent_ - 1; ic++ ) {
                sbuf.append( ' ' );
            }
            return sbuf.toString();
        }
    }

    /**
     * Returns prepended whitespace.
     *
     * @param  level  indentation level
     * @return  string to prepend
     */
    private String getIndent( int level ) {
        if ( indent_ >= 0 ) {
            int nc = level * indent_;
            StringBuffer sbuf = new StringBuffer( nc + 1 );
            sbuf.append( '\n' );
            for ( int ic = 0; ic < nc; ic++ ) {
                sbuf.append( ' ' );
            }
            return sbuf.toString();
        }
        else {
            return "";
        }
    }

    public static void main( String[] args ) {
        String txt = args[ 0 ];
        Object item = new JsonReader().read( txt );
        System.out.println( new JsonWriter().toJson( item ) );
    }
}
