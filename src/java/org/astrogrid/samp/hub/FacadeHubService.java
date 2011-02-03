package org.astrogrid.samp.hub;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * HubService that provides hub functionality by accessing an existing
 * hub service.  The existing hub service is defined by a supplied
 * ClientProfile object.
 *
 * @author   Mark Taylor
 * @since    1 Feb 2011
 */
public class FacadeHubService implements HubService {

    private final ClientProfile profile_;
    private final Set connectionSet_;
    private static final Logger logger_ =
        Logger.getLogger( FacadeHubService.class.getName() );

    /**
     * Constructor.
     *
     * @param   profile  defines the hub connection factory on which this
     *          service is based
     */
    public FacadeHubService( ClientProfile profile ) {
        profile_ = profile;
        connectionSet_ = Collections.synchronizedSet( new HashSet() );
    }

    public boolean isHubRunning() {
        return profile_.isHubRunning();
    }

    public HubConnection register() throws SampException {

        // Mostly delegate registration to the underlying client profile,
        // but put in place machinery to keep track of which clients
        // are registered via this service.  This will be required so that
        // they can be messaged if the underlying hub shuts down.
        HubConnection baseConnection = profile_.register();
        if ( baseConnection != null ) {
            HubConnection conn = new FacadeHubConnection( baseConnection ) {
                final HubConnection hubConn = this;
                public void ping() throws SampException {
                    if ( FacadeHubService.this.isHubRunning() ) {
                        super.ping();
                    }
                    else {
                        throw new SampException( "Hub underlying facade "
                                               + "is not running" );
                    }
                }
                public void unregister() throws SampException {
                    connectionSet_.remove( hubConn );
                    super.unregister();
                }
            };
            connectionSet_.add( conn );
            return conn;
        }

        // Or return null if there is no underlying hub.
        else {
            return null;
        }
    }

    /**
     * No-op.
     */
    public void start() {
    }

    public void shutdown() {
        hubEvent( new Message( "samp.hub.event.shutdown" ) );
        connectionSet_.clear();
    }

    /**
     * Sends a given message by notification, as if from the hub,
     * to all the clients which have registered through this service.
     *
     * @param  msg  message to send
     */
    private void hubEvent( Message msg ) {
        String mtype = msg.getMType();
        FacadeHubConnection[] connections =
            (FacadeHubConnection[])
            connectionSet_.toArray( new FacadeHubConnection[ 0 ] );
        for ( int ic = 0; ic < connections.length; ic++ ) {
            connections[ ic ].hubEvent( msg );
        }
    }

    /**
     * Utility HubConnection class which allows hub event notifications
     * to be sent to clients.
     */
    private static class FacadeHubConnection extends WrapperHubConnection {
        private CallableClient callable_;
        private Subscriptions subs_;

        /**
         * Constructor.
         *
         * @param   base  base connection
         */
        FacadeHubConnection( HubConnection base ) {
            super( base );
        }

        public void setCallable( CallableClient callable )
                throws SampException {
            super.setCallable( callable );
            callable_ = callable;
        }

        public void declareSubscriptions( Map subs ) throws SampException {
            super.declareSubscriptions( subs );
            subs_ = subs == null ? null
                                 : Subscriptions.asSubscriptions( subs );
        }

        /**
         * Sends a given message as a notification, as if from the hub,
         * to this connection if it is able to receive it.
         *
         * @param  msg  message to send
         */
        void hubEvent( Message msg ) {
            String mtype = msg.getMType();
            CallableClient callable = callable_;
            if ( callable != null && subs_.isSubscribed( mtype ) ) {
                RegInfo regInfo = getRegInfo();
                try {
                    callable.receiveNotification( regInfo.getHubId(), msg );
                }
                catch ( Throwable e ) {
                    logger_.info( "Failed " + mtype + " notification to "
                                + regInfo.getSelfId() );
                }
            }
        }
    }
}
