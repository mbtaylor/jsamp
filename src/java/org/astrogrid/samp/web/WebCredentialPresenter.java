package org.astrogrid.samp.web;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * CredentialPresenter for use with the Web Profile.
 *
 * <p>Uses the following securityMap items:
 * <dl>
 * <dt>samp.name:</dt>
 * <dd>Self-declared client name.
 *     Mandatory, but since it's supplied by the client, it doesn't tell
 *     you anything trustworthy.
 *     </dd>
 * </dl>
 * and the following HTTP headers:
 * <dl>
 * <dt>Origin:</dt>
 * <dd>Application origin, present if CORS is in use.
 *     See <a href="http://www.w3.org/TR/cors/"
 *            >Cross-Origin Resource Sharing</a>,
 *         <a href="https://www.ietf.org/rfc/rfc6454.txt"
 *            >The Web Origin Concept (RFC 6454)</a>.
 *     </dd>
 * <dt>Referer:</dt>
 * <dd>Connection URL, present at whim of browser.
 *     See <a href="https://www.w3.org/Protocols/rfc2616/rfc2616.html"
 *            >HTTP/1.1 (RFC2616)</a>, sec 14.36.
 *     </dd>
 * </dl>
 *
 * <p>The sole instance of this singleton class is {@link #INSTANCE}.
 *
 * @author   Mark Taylor
 * @since    20 Jun 2016
 */
public class WebCredentialPresenter implements CredentialPresenter {

    /** Singleton instance. */
    public static final WebCredentialPresenter INSTANCE =
        new WebCredentialPresenter();

    /** Origin header. */
    public static final String ORIGIN_HDR = "Origin";

    /** Referer header. */
    public static final String REFERER_HDR = "Referer";

    private static final Logger logger_ =
        Logger.getLogger( WebCredentialPresenter.class.getName() );

    /**
     * Private sole constructor prevents external instantiation.
     */
    private WebCredentialPresenter() {
    }

    public Presentation
            createPresentation( HttpServer.Request request, Map securityMap,
                                AuthResourceBundle.Content authContent )
            throws SampException {

        // Application self-declared name.  Required, but doesn't prove much.
        String appName = ClientAuthorizers.getAppName( securityMap );

        // Application origin (see http://www.w3.org/TR/cors/,
        // http://tools.ietf.org/html/draft-abarth-origin);
        // present if CORS is in use.
        Map headerMap = request.getHeaderMap();
        String origin = HttpServer.getHeader( headerMap, ORIGIN_HDR );

        // Referer header (RFC2616 sec 14.36) - present at whim of browser.
        String referer = HttpServer.getHeader( headerMap, REFERER_HDR );

        final Map map = new LinkedHashMap();
        map.put( authContent.nameWord(), appName );
        map.put( authContent.originWord(), origin );
        map.put( "URL", referer );

        final Object[] lines;
        if ( referer != null && origin != null &&
             ! origin.equals( getOrigin( referer ) ) ) {
            logger_.warning( "Origin/Referer header mismatch: "
                           + "\"" + origin + "\" != "
                           + "\"" + getOrigin( referer ) + "\"" );
            lines = new Object[] {
                "WARNING: Origin/Referer header mismatch!",
                "WARNING: This looks suspicious.",
            };
        }
        else {
            lines = new Object[ 0 ];
        }
        return new Presentation() {
            public Map getAuthEntries() {
                return map;
            }
            public Object[] getAuthLines() {
                return lines;
            }
        };
    }

    /**
     * Returns the serialized origin for a given URI string.
     * @see <a href="http://tools.ietf.org/html/draft-abarth-origin-09"
     *         >The Web Origin Concept</a>
     *
     * @param  uri  URI
     * @return  origin of <code>uri</code>,
     *          <code>null</code> (note: not "null") if it cannot be determined
     */
    private String getOrigin( String uri ) {
        if ( uri == null ) {
            return null;
        }
        URL url;
        try {
            url = new URL( uri );
        }
        catch ( MalformedURLException e ) {
            return null;
        }
        String scheme = url.getProtocol();
        String host = url.getHost();
        int portnum = url.getPort();
        StringBuffer sbuf = new StringBuffer()
            .append( scheme )
            .append( "://" )
            .append( host );
        if ( portnum >= 0 && portnum != url.getDefaultPort() ) {
            sbuf.append( ":" )
                .append( Integer.toString( portnum ) );
        }
        return sbuf.toString().toLowerCase();
    }
}
