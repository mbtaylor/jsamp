package org.astrogrid.samp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Represents the application metadata associated with a SAMP client.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2008
 */
public class Metadata extends SampMap {

    /** Key for application name. */
    public static final String NAME_KEY = "samp.name";

    /** Key for short description of the application in plain text. */
    public static final String DESCTEXT_KEY = "samp.description.text";

    /** Key for description of the application in HTML. */
    public static final String DESCHTML_KEY = "samp.description.html";

    /** Key for the URL of an icon in png, gif or jpeg format. */
    public static final String ICONURL_KEY = "samp.icon.url";

    /** Key for the URL of a documentation web page. */
    public static final String DOCURL_KEY = "samp.documentation.url";

    private static final String[] KNOWN_KEYS = new String[] {
        NAME_KEY,
        DESCTEXT_KEY,
        DESCHTML_KEY,
        ICONURL_KEY,
        DOCURL_KEY,
    };

    /**
     * Constructs an empty Metadata map.
     */
    public Metadata() {
        super( KNOWN_KEYS );
    }

    /**
     * Constructs a Metadata map based on a given map.
     *
     * @param  map  map containing initial values for this object
     */
    public Metadata( Map map ) {
        this();
        putAll( map );
    }

    /**
     * Sets the value for the application's name.
     *
     * @param  name  value for {@link #NAME_KEY} key
     */
    public void setName( String name ) {
        put( NAME_KEY, name );
    }

    /**
     * Returns the value for the application's name.
     *
     * @return value for {@link #NAME_KEY} key
     */
    public String getName() {
        return (String) get( NAME_KEY );
    }

    /**
     * Sets a short description of the application.
     *
     * @param  txt  value for {@link #DESCTEXT_KEY} key
     */
    public void setDescriptionText( String txt ) {
        put( DESCTEXT_KEY, txt );
    }

    /**
     * Returns a short description of the application.
     *
     * @return  value for {@link #DESCTEXT_KEY} key
     */
    public String getDescriptionText() {
        return (String) get( DESCTEXT_KEY );
    }

    /**
     * Sets an HTML description of the application.
     *
     * @param  html  value for {@link #DESCHTML_KEY} key
     */
    public void setDescriptionHtml( String html ) {
        put( DESCHTML_KEY, html );
    }

    /**
     * Returns an HTML description of the application.
     *
     * @return  value for {@link #DESCHTML_KEY} key
     */
    public String getDescriptionHtml() {
        return (String) get( DESCHTML_KEY );
    }

    /**
     * Sets a URL for a gif, png or jpeg icon identifying the application.
     *
     * @param  url  value for {@link #ICONURL_KEY} key
     */
    public void setIconUrl( String url ) {
        put( ICONURL_KEY, url );
    }

    /**
     * Returns a URL for a gif, png or jpeg icon identifying the application.
     *
     * @return   value for {@link #ICONURL_KEY} key
     */
    public URL getIconUrl() {
        return getUrl( ICONURL_KEY );
    }

    /**
     * Sets a URL for a documentation web page.
     * 
     * @param  url  value for {@link #DOCURL_KEY} key
     */ 
    public void setDocumentationUrl( String url ) {
        put( DOCURL_KEY, url );
    }

    /**
     * Returns a URL for a documentation web page.
     *
     * @return  value for {@link #DOCURL_KEY} key
     */
    public URL getDocumentationUrl() {
        return getUrl( DOCURL_KEY );
    }

    public void check() {
        super.check();
        SampUtils.checkUrl( getString( DOCURL_KEY ) );
        SampUtils.checkUrl( getString( ICONURL_KEY ) );
    }

    /**
     * Returns a given map as a Metadata object.
     *
     * @param   map  map
     * @return  metadata
     */
    public static Metadata asMetadata( Map map ) {
        return ( map instanceof Metadata || map == null )
             ? (Metadata) map
             : new Metadata( map );
    }
}
