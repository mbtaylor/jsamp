package org.astrogrid.samp.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.HubConnection;

/**
 * Implements MType for querying registered clients by metadata item.
 *
 * @author   Mark Taylor
 * @since    21 Nov 2011
 */
class MetaQueryMessageHandler extends AbstractMessageHandler {

    private final ClientSet clientSet_;
    private static final String BASE_MTYPE = "query.by-meta";

    /**
     * Constructor.
     *
     * @param  clientSet  hub client set object
     */
    public MetaQueryMessageHandler( ClientSet clientSet ) {
        super( new String[] { "samp." + BASE_MTYPE, "x-samp." + BASE_MTYPE } );
        clientSet_ = clientSet;
    }

    public Map processCall( HubConnection conn, String senderId, Message msg ) {
        String key = (String) msg.getRequiredParam( "key" );
        String value = (String) msg.getRequiredParam( "value" );
        HubClient[] clients = clientSet_.getClients();
        List foundList = new ArrayList();
        for ( int ic = 0; ic < clients.length; ic++ ) {
            HubClient client = clients[ ic ];
            Metadata meta = client.getMetadata();
            if ( meta != null && value.equals( meta.get( key ) ) ) {
                foundList.add( client.getId() );
            }
        }
        Map result = new HashMap();
        result.put( "ids", foundList );
        return result;
    }
}
