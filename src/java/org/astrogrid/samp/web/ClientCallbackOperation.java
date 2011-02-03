package org.astrogrid.samp.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.client.CallableClient;

/**
 * Represents one of the possible callbacks which may be invoked on a
 * CallableClient.  The {@link #invoke} static method arranges for a Callback
 * acquired from the hub to be dispatched to a CallableClient.
 *
 * @author   Mark Taylor
 * @since    3 Feb 2011
 */
abstract class ClientCallbackOperation {

    private final String fqName_; 
    private final Class[] sampSig_;
    private static final Map OPERATION_MAP = createOperationMap();

    /**
     * Constructor.
     *
     * @param  unqualified callback method name
     * @param  signature of callback; an array of SAMP-friendly classes,
     *         one for each argument
     */
    private ClientCallbackOperation( String methodName, Class[] sampSig ) {
        fqName_ = WebClientProfile.WEBSAMP_CLIENT_PREFIX + methodName;
        sampSig_ = sampSig;
    }
                                   
    /**
     * Makes a call to a callable client of the method represented by 
     * this operation with a given list of parameters.
     * No checking is performed on the parameter list.
     *
     * <p>This method should be private really, but abstract private is
     * not permitted.
     *
     * @param  client   target callable client
     * @param  paramList  parameters for call, assumed to be valid
     */
    abstract void dispatch( CallableClient client, List paramList )
            throws Exception;
                        
    /**
     * Dispatches a callback to a CallableClient.
     *
     * @param  callback  callback acquired from the hub
     * @param  client  client which should execute <code>callback</code>
     */
    public static void invoke( Callback callback, CallableClient client )
            throws Exception { 
        callback.check();
        String methodName = callback.getMethodName();
        List paramList = callback.getParams();
        ClientCallbackOperation op =
            (ClientCallbackOperation) OPERATION_MAP.get( methodName );
        if ( op == null ) {
            throw new UnsupportedOperationException(
                "Unknown callback operation " + methodName );
        }
        else {
            boolean sigOk = op.sampSig_.length == paramList.size();
            for ( int i = 0; sigOk && i < op.sampSig_.length; i++ ) {
                sigOk = sigOk
                     && op.sampSig_[ i ]
                       .isAssignableFrom( paramList.get( i ).getClass() );
            }
            if ( ! sigOk ) {
                throw new IllegalArgumentException(
                    methodName + " callback signature mismatch" );
            }
            else {
                op.dispatch( client, paramList );
            }
        }
    }

    /**
     * Returns a map, keyed by unqualified operation name,
     * of known callback operations.
     *
     * @param   String->ClientCallbackOperation map
     */
    private static Map createOperationMap() {

        // First assemble an array of known callback operations.
        // It would be possible to assemble this array using reflection
        // on the CallableClient interface, but more trouble than it's
        // worth for three methods.
        ClientCallbackOperation[] operations = new ClientCallbackOperation[] {
            new ClientCallbackOperation( "receiveNotification",
                                         new Class[] { String.class,
                                                       Map.class } ) {
                public void dispatch( CallableClient client, List params )
                        throws Exception {
                    client.receiveNotification(
                               (String) params.get( 0 ),
                               new Message( (Map) params.get( 1 ) ) );
                }
            },
            new ClientCallbackOperation( "receiveCall",
                                         new Class[] { String.class,
                                                       String.class,
                                                       Map.class } ) {
                public void dispatch( CallableClient client, List params )
                        throws Exception {
                    client.receiveCall(
                               (String) params.get( 0 ),
                               (String) params.get( 1 ),
                               new Message( (Map) params.get( 2 ) ) );
                }
            },
            new ClientCallbackOperation( "receiveResponse",
                                         new Class[] { String.class,
                                                       String.class,
                                                       Map.class } ) {
                public void dispatch( CallableClient client, List params )
                        throws Exception {
                    client.receiveResponse(
                               (String) params.get( 0 ),
                               (String) params.get( 1 ),
                               new Response( (Map) params.get( 2 ) ) );
                }
            },
        };

        // Turn it into a map keyed by operation name, and return.
        Map opMap = new HashMap();
        for ( int i = 0; i < operations.length; i++ ) {
            ClientCallbackOperation op = operations[ i ];
            opMap.put( op.fqName_, op );
        }
        return Collections.unmodifiableMap( opMap );
    }
}
