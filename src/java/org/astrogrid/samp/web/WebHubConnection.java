package org.astrogrid.samp.web;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.xmlrpc.SampXmlRpcClient;
import org.astrogrid.samp.xmlrpc.XmlRpcHubConnection;

/**
 * HubConnection implementation for the Web Profile.
 *
 * @author   Mark Taylor
 * @since    3 Feb 2011
 */
class WebHubConnection extends XmlRpcHubConnection {

    private final String appName_;
    private final String clientKey_;
    private CallWorker callWorker_;
    private static Logger logger_ =
        Logger.getLogger( WebHubConnection.class.getName() );

    /**
     * Constructor.
     *
     * @param   xClient  XML-RPC client
     * @param   appName  client's declared name
     */
    public WebHubConnection( SampXmlRpcClient xClient, String appName )
            throws SampException {
        super( xClient, WebClientProfile.WEBSAMP_HUB_PREFIX,
               Collections.singletonList( appName ) );
        appName_ = appName;
        clientKey_ = getRegInfo().getPrivateKey();
    }

    public Object getClientKey() {
        return clientKey_;
    }

    public void setCallable( CallableClient client ) throws SampException {
        CallWorker oldWorker = callWorker_;
        callWorker_ = null;
        if ( oldWorker != null ) {
            oldWorker.stopped_ = true;
        }
        exec( "allowReverseCallbacks",
              new Object[] { SampUtils.encodeBoolean( client != null ) } );
        if ( client != null ) {
            CallWorker callWorker = new CallWorker( this, client, appName_ );
            callWorker.start();
            callWorker_ = callWorker;
        }
    }

    /**
     * Thread that performs repeated long polls to pull callbacks from the
     * hub and passes them on to this connection's CallableClient for
     * execution.
     */
    private static class CallWorker extends Thread {

        private final XmlRpcHubConnection xconn_;
        private final CallableClient client_;
        private final int timeoutSec_ = 60 * 10;
        private final long minWaitMillis_ = 5 * 1000;
        private volatile boolean stopped_;

        /**
         * Constructor.
         *
         * @param  xconn  hub connection
         * @parma  client  callable client
         * @param  appName  client's name
         */
        CallWorker( XmlRpcHubConnection xconn, CallableClient client,
                    String appName ) {
            super( "Web Profile Callback Puller for " + appName );
            xconn_ = xconn;
            client_ = client;
            setDaemon( true );
        }

        public void run() {
            String stimeout = SampUtils.encodeInt( timeoutSec_ );
            while ( true && ! stopped_ ) {
                long start = System.currentTimeMillis();
                Object result;
                try {
                    result = xconn_.exec( "pullCallbacks",
                                          new Object[] { stimeout } );
                }
                catch ( Exception e ) {
                    long wait = System.currentTimeMillis() - start;
                    if ( wait < minWaitMillis_ ) {
                        seriousError( e );
                    }
                    else {
                        logger_.config( "pullCallbacks timeout? "
                                      + ( wait / 1000 ) + "s" );
                    }
                    break;
                }
                catch ( Throwable e ) {
                    seriousError( e );
                    break;
                }
                if ( ! stopped_ ) {
                    if ( result instanceof List ) {
                        List resultList = (List) result;
                        for ( Iterator it = resultList.iterator();
                              it.hasNext(); ) {
                            try {
                                final Callback cb =
                                    new Callback( (Map) it.next() );
                                new Thread( "Web Profile Callback" ) {
                                    public void run() {
                                        try {
                                            ClientCallbackOperation
                                                .invoke( cb, client_ );
                                        }
                                        catch ( Throwable e ) {
                                            logger_.log( Level.WARNING,
                                                         "Callback failure: "
                                                       + e.getMessage(), e );
                                        }
                                    }
                                }.start();
                            }
                            catch ( Throwable e ) {
                                logger_.log( Level.WARNING, e.getMessage(), e );
                            }
                        }
                    }
                    else {
                        logger_.warning( "pullCallbacks result "
                                       + "is not a List - ignore" );
                    }
                }
            }
        }

        /**
         * Invoked if there is a serious (non-timeout) error when polling
         * for callbacks.  This currently stops the polling for good.
         * That may be a drastic response, but at least it prevents
         * repeated high-frequency polling attempts to a broken server,
         * which might otherwise result.
         *
         * @parm  e  error which caused the trouble
         */
        private void seriousError( Throwable e ) {
            stopped_ = true;
            logger_.log( Level.WARNING,
                         "Fatal pullCallbacks error - stopped listening", e );
        }
    }
}
