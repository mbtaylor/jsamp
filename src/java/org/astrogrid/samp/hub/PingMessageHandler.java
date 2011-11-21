package org.astrogrid.samp.hub;

import java.util.HashMap;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;

/**
 * Implements samp.app.ping MType.
 *
 * @author   Mark Taylor
 * @since    21 Nov 2011
 */
class PingMessageHandler extends AbstractMessageHandler {

    /**
     * Constructor.
     */
    public PingMessageHandler() {
        super( "samp.app.ping" );
    }

    public Map processCall( HubConnection conn, String senderId, Message msg ) {
        return new HashMap();
    }
};
