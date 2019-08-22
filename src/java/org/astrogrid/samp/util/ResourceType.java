package org.astrogrid.samp.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Message;

/**
 * Defines one of the types of resource that can be turned into a
 * SAMP load-type message.
 *
 * @author   Mark Taylor
 * @since    21 Aug 2019
 */
public abstract class ResourceType {

    private final String name_;
    private final String mtype_;
    private final String[] ctypes_;

    /** Resource type for table.load.votable. */
    public static final ResourceType RTYPE_VOTABLE;

    /** Resource type for image.load.fits. */
    public static final ResourceType RTYPE_FITS;

    /** Resource type for table.load.cdf. */
    public static final ResourceType RTYPE_CDF;

    private static final ResourceType[] RESOURCE_TYPES = {
        RTYPE_VOTABLE = createVOTableResourceType(),
        RTYPE_FITS = createFitsImageResourceType(),
        RTYPE_CDF = createCdfTableResourceType(),
    };
    private static final int MAGIC_SIZE = 1024;
    private static final Logger logger_ =
        Logger.getLogger( ResourceType.class.getName() );

    /**
     * Constructor.
     *
     * @param  name  identifying name
     * @param  mtype  MType of message that will be sent
     * @param  ctypes  MIME-types to which this corresponds,
     *                 supplied in normalised form
     *                 (lower case, no parameters, no whitespace)
     */
    public ResourceType( String name, String mtype, String[] ctypes ) {
        name_ = name;
        mtype_ = mtype;
        ctypes_ = ctypes;
    }

    /**
     * Returns the MType of the message to be constructed.
     *
     * @return  MType string
     */
    public String getMType() {
        return mtype_;
    }

    /**
     * Returns a Message object that will forward a given URL to SAMP
     * clients.
     *
     * @param  url  URL of resource
     * @return  message instance
     */
    public Message createMessage( URL url ) {
        Map params = new LinkedHashMap();
        params.put( "url", url.toString() );
        return new Message( mtype_, params );
    }

    /**
     * Indicates whether this resource type is suitable for use
     * with a given MIME type.  Note that the submitted content type
     * may contain additional parameters and have embedded whitespace etc
     * as permitted by RFC 2045.
     *
     * @param  ctype  content-type header value
     * @return   true iff this resource type is suitable for use with
     *           the given content type
     */
    public boolean isContentType( String ctype ) {
        ctype = ctype.replaceAll( " *;.*", "" )
                     .replaceAll( "\\s+", "" )
                     .toLowerCase();
        for ( int ic = 0; ic < ctypes_.length; ic++ ) {
            if ( ctype.startsWith( ctypes_[ ic ] ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates whether this resource type is suitable for use
     * with a resource having a given magic number.
     *
     * @param   magic  buffer containing the first few bytes of
     *                 resource content
     * @return  true iff this resource type is suitable for use
     *          with the given content
     */
    public abstract boolean isMagic( byte[] magic );

    /**
     * Returns the name of this resource type.
     *
     * @return  name
     */
    public String getName() {
        return name_;
    }

    public String toString() {
        return name_;
    }

    /**
     * Returns the known resource types.
     *
     * @return  known instances of this class
     */
    public static ResourceType[] getKnownResourceTypes() {
        return (ResourceType[]) RESOURCE_TYPES.clone();
    }

    /**
     * Attempts to determine the resource type of a given URL by
     * making an HTTP HEAD request and looking at the Content-Type.
     *
     * @param  url  resource location
     * @return   good guess at resource type, or null if can't be determined
     */
    public static ResourceType readHeadResourceType( URL url ) {
        try {
            URLConnection uconn = url.openConnection();
            if ( uconn instanceof HttpURLConnection ) {
                logger_.info( "HEAD " + url );
                HttpURLConnection hconn = (HttpURLConnection) uconn;
                hconn.setInstanceFollowRedirects( true );
                hconn.setRequestMethod( "HEAD" );
                hconn.connect();
                int code = hconn.getResponseCode();
                if ( code == 200 ) {
                    String ctype = hconn.getContentType();
                    logger_.info( "HEAD Content-Type: " + ctype );
                    return getMimeResourceType( ctype );
                }
                else {
                    logger_.warning( "HEAD response code " + code );
                    return null;
                }
            }
            else {
                return null;
            }
        }
        catch ( IOException e ) {
            logger_.log( Level.WARNING, "HEAD failed", e );
            return null;
        }
    }

    /**
     * Attempts to determine the resource type of a given URL by
     * downloading the first part of its content and examining the
     * magic number.
     *
     * @param  url  resource location
     * @return  good guess at resource type, or null if it can't be determined
     */
    public static ResourceType readContentResourceType( URL url ) {

        // Acquire the magic number.
        byte[] buf = new byte[ MAGIC_SIZE ];
        InputStream in = null;
        logger_.info( "GET " + url );
        try {

            // Open a GET connection.
            URLConnection uconn = url.openConnection();
            if ( uconn instanceof HttpURLConnection ) {
                HttpURLConnection hconn = (HttpURLConnection) uconn;
                hconn.setInstanceFollowRedirects( true );
                hconn.connect();
                int code = hconn.getResponseCode();
                if ( code != 200 ) {
                    logger_.warning( "GET response code " + code );
                    return null;
                }

                // The content-type may be usable here, even if the
                // presumed earlier call to HEAD failed
                // (for instance HEAD not implemented).
                String ctype = hconn.getContentType();
                ResourceType rtype = getMimeResourceType( ctype );
                if ( rtype != null ) {
                    logger_.info( "GET Content-Type: " + ctype );
                    return rtype;
                }
            }
            else {
                uconn.connect();
            }

            // Read the first few bytes into a buffer, then close the stream.
            in = uconn.getInputStream();
            for ( int off = 0; off < MAGIC_SIZE; ) {
                int nr = in.read( buf, off, MAGIC_SIZE - off );
                if ( nr > 0 ) {
                    off += nr;
                }
                else {
                    break;
                }
            }
        }
        catch ( IOException e ) {
            logger_.log( Level.WARNING, "GET failed", e );
        }
        finally {
            if ( in != null ) {
                try {
                    in.close();
                }
                catch ( IOException e ) {
                }
            }
        }

        // Try to determine type from magic number.
        return getMagicResourceType( buf );
    }

    /**
     * Try to identify a resource type from its MIME type.
     *
     * @param  contentType  content-type header
     * @return   resource type, or null if not known
     */
    private static ResourceType getMimeResourceType( String contentType ) {
        if ( contentType != null ) {
            for ( int i = 0; i < RESOURCE_TYPES.length; i++ ) {
                ResourceType rtype = RESOURCE_TYPES[ i ];
                if ( rtype.isContentType( contentType ) ) {
                    return rtype;
                }
            }
        }
        return null;
    }

    /**
     * Try to identify a resource type from its magic number.
     *
     * @param  magic  buffer containing first few bytes of resource content
     * @return  resource type, or null if not known
     */
    private static ResourceType getMagicResourceType( byte[] magic ) {
        for ( int i = 0; i < RESOURCE_TYPES.length; i++ ) {
            ResourceType rtype = RESOURCE_TYPES[ i ];
            if ( rtype.isMagic( magic ) ) {
                logger_.info( "GET magic number looks like " + rtype );
                return rtype;
            }
        }
        return null;
    }

    /**
     * Returns a ResourceType instance suitable for the table.load.votable
     * SAMP MType.
     *
     * @return  VOTable resource type
     */
    private static ResourceType createVOTableResourceType() {
        return new ResourceType( "VOTable", "table.load.votable",
                                 new String[] { "application/x-votable+xml",
                                                "text/xml" } ) {
            public boolean isMagic( byte[] buf ) {
                // Shocking hack that should work for UTF-8 and UTF-16*.
                String txt;
                try {
                    txt = new String( buf, "US-ASCII" );
                }
                catch ( UnsupportedEncodingException e ) {
                    return false;
                }
                return txt.contains( "<VOTABLE" )
                    || txt.contains( "<\0V\0O\0T\0A\0B\0L\0E" );
            }
        };
    }

    /**
     * Returns a ResourceType instance suitable for the image.load.fits
     * SAMP MType.
     *
     * @return  FITS image resource type
     */
    private static ResourceType createFitsImageResourceType() {
        return new ResourceType( "FITS", "image.load.fits",
                                 new String[] { "image/fits",
                                                "application/fits" } ) {
            public boolean isMagic( byte[] buf ) {
                return buf.length >= 9 &&
                       (char) buf[ 0 ] == 'S' &&
                       (char) buf[ 1 ] == 'I' &&
                       (char) buf[ 2 ] == 'M' &&
                       (char) buf[ 3 ] == 'P' &&
                       (char) buf[ 4 ] == 'L' &&
                       (char) buf[ 5 ] == 'E' &&
                       (char) buf[ 6 ] == ' ' &&
                       (char) buf[ 7 ] == ' ' &&
                       (char) buf[ 8 ] == '=';
            }
        };
    }

    /**
     * Returns a ResourceType instance suitable for the table.load.cdf
     * SAMP Mtype.
     *
     * @return  CDF table resource type
     */
    private static ResourceType createCdfTableResourceType() {
        return new ResourceType( "CDF", "table.load.cdf", new String[ 0 ] ) {
            public boolean isMagic( byte[] buf ) {
                if ( buf.length >= 8 ) {
                    int m1 = ( buf[ 0 ] & 0xff ) << 24
                           | ( buf[ 1 ] & 0xff ) << 16
                           | ( buf[ 2 ] & 0xff ) <<  8
                           | ( buf[ 3 ] & 0xff ) <<  0;
                    int m2 = ( buf[ 4 ] & 0xff ) << 24
                           | ( buf[ 5 ] & 0xff ) << 16
                           | ( buf[ 6 ] & 0xff ) <<  8
                           | ( buf[ 7 ] & 0xff ) <<  0;
                    // Version 2.6+ only.
                    return ( m1 == 0xcdf30001 || m1 == 0xcdf26002 )
                        && ( m2 == 0x0000ffff || m2 == 0xcccc0001 );
                }
                else {
                    return false;
                }
            }
        };
    }
}
