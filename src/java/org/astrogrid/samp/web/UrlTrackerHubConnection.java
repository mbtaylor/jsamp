package org.astrogrid.samp.web;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * HubConnection wrapper implementation which intercepts all incoming 
 * and outgoing communications, scans them for URLs in the payload,
 * and notifies a supplied UrlTracker object.
 *
 * @author   Mark Taylor
 * @since    22 Jul 2011
 */
class UrlTrackerHubConnection implements HubConnection {

    private final HubConnection base_;
    private final UrlTracker urlTracker_;

    /**
     * Constructor.
     *
     * @param   base   connection on which this one is based
     * @param  urlTracker  tracker for URL usage
     */
    public UrlTrackerHubConnection( HubConnection base,
                                    UrlTracker urlTracker ) {
        base_ = base;
        urlTracker_ = urlTracker;
    }

    /**
     * Recursively scans a SAMP map for items that look like URLs,
     * and notifies the tracker that they are incoming.
     * As a convenience the input map is returned.
     *
     * @param  map  map to scan
     * @return  the input map, unchanged
     */
    private Map scanIncoming( Map map ) {
        URL[] urls = scanForUrls( map );
        for ( int iu = 0; iu < urls.length; iu++ ) {
            urlTracker_.noteIncomingUrl( urls[ iu ] );
        }
        return map;
    }

    /**
     * Recursively scans a SAMP map for items that look like URLs,
     * and notifies the tracker that they are outgoing.
     * As a convenience the input map is returned.
     *
     * @param  map  map to scan
     * @return  the input map, unchanged
     */
    private Map scanOutgoing( Map map ) {
        URL[] urls = scanForUrls( map );
        for ( int iu = 0; iu < urls.length; iu++ ) {
            urlTracker_.noteOutgoingUrl( urls[ iu ] );
        }
        return map;
    }

    /**
     * Recursively scans a map for items that look like URLs and
     * returns them as an array.
     *
     * @param  map  map to scan
     * @return   array of URLs found in <code>map</code>
     */
    private URL[] scanForUrls( Map map ) {
        Collection urlSet = new HashSet();
        scanForUrls( map, urlSet );
        return (URL[]) urlSet.toArray( new URL[ 0 ] );
    }

    /**
     * Recursively scans a SAMP data item for items that look like URLs
     * and appends them into a supplied list.
     *
     * @param   item  SAMP data item (String, List or Map)
     * @param  urlSet  list of URL objects to which URLs can be added
     */
    private void scanForUrls( Object item, Collection urlSet ) {
        if ( item instanceof String ) {
            if ( isUrl( (String) item ) ) {
                try {
                    urlSet.add( new URL( (String) item ) );
                }
                catch ( MalformedURLException e ) {
                }
            }
        }
        else if ( item instanceof List ) {
            for ( Iterator it = ((List) item).iterator(); it.hasNext(); ) {
                scanForUrls( it.next(), urlSet );
            }
        }
        else if ( item instanceof Map ) {
            for ( Iterator it = ((Map) item).values().iterator();
                  it.hasNext(); ) {
                scanForUrls( it.next(), urlSet );
            }
        }
    }

    /**
     * Determines whether a given string is apparently a URL.
     *
     * @param  str  string to test
     * @return  true iff <code>str</code> looks like a URL
     */
    private boolean isUrl( String str ) {
        if ( str == null || str.indexOf( ":/" ) <= 0 ) {
            return false;
        }
        else {
            try {
                new URL( str ); 
                return true;
            }
            catch ( MalformedURLException e ) {
                return false;
            }
        }
    }

    public void setCallable( CallableClient callable ) throws SampException {
        base_.setCallable( new UrlTrackerCallableClient( callable ) );
    }

    public void notify( String recipientId, Map msg ) throws SampException {
        base_.notify( recipientId, scanOutgoing( msg ) );
    }

    public List notifyAll( Map msg ) throws SampException {
        return base_.notifyAll( scanOutgoing( msg ) );
    }

    public String call( String recipientId, String msgTag, Map msg )
            throws SampException {
        return base_.call( recipientId, msgTag, scanOutgoing( msg ) );
    }

    public Map callAll( String msgTag, Map msg ) throws SampException {
        return base_.callAll( msgTag, scanOutgoing( msg ) );
    }

    public Response callAndWait( String recipientId, Map msg, int timeout )
            throws SampException {
        return (Response)
               scanIncoming( base_.callAndWait( recipientId,
                                                scanOutgoing( msg ),
                                                timeout ) );
    }

    public void reply( String msgId, Map response ) throws SampException {
        base_.reply( msgId, scanOutgoing( response ) );
    }

    public RegInfo getRegInfo() {
        return (RegInfo) scanIncoming( base_.getRegInfo() );
    }

    public void ping() throws SampException {
        base_.ping();
    }

    public void unregister() throws SampException {
        base_.unregister();
    }

    public void declareMetadata( Map meta ) throws SampException {
        base_.declareMetadata( scanOutgoing( meta ) );
    }

    public Metadata getMetadata( String clientId ) throws SampException {
        return (Metadata) scanIncoming( base_.getMetadata( clientId ) );
    }

    public void declareSubscriptions( Map subs ) throws SampException {
        base_.declareSubscriptions( scanOutgoing( subs ) );
    }

    public Subscriptions getSubscriptions( String clientId )
            throws SampException {
        return (Subscriptions)
               scanIncoming( base_.getSubscriptions( clientId ) );
    }

    public String[] getRegisteredClients() throws SampException {
        return base_.getRegisteredClients();
    }

    public Map getSubscribedClients( String mtype ) throws SampException {
        return scanIncoming( base_.getSubscribedClients( mtype ) );
    }

    /**
     * CallableClient wrapper implementation which intercepts
     * communications, scans the payloads for URLs, and informs an
     * associated UrlTracker.
     */
    private class UrlTrackerCallableClient implements CallableClient {
        private final CallableClient baseCallable_;

        /**
         * Constructor.
         *
         * @param  baseCallable  object on which this one is based
         */
        UrlTrackerCallableClient( CallableClient baseCallable ) {
            baseCallable_ = baseCallable;
        }

        public void receiveCall( String senderId, String msgId, Message msg )
                throws Exception {
            baseCallable_.receiveCall( senderId, msgId,
                                       (Message) scanIncoming( msg ) );
        }

        public void receiveNotification( String senderId, Message msg )
                throws Exception {
            baseCallable_.receiveNotification( senderId,
                                               (Message) scanIncoming( msg ) );
        }

        public void receiveResponse( String responderId, String msgTag,
                                     Response response )
                throws Exception {
            baseCallable_.receiveResponse( responderId, msgTag,
                                           (Response)
                                           scanIncoming( response ) );
        }
    }
}
