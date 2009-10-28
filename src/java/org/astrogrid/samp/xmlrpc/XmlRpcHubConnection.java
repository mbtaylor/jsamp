package org.astrogrid.samp.xmlrpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;

/**
 * HubConnection implementation based on XML-RPC as per the SAMP 
 * Standard Profile.
 *
 * @author   Mark Taylor
 * @since    16 Jul 2008
 */
public class XmlRpcHubConnection implements HubConnection {

    private final SampXmlRpcClient xClient_;
    private final SampXmlRpcServerFactory serverFactory_;
    private final RegInfo regInfo_;
    private CallableClientServer callableServer_;
    private boolean unregistered_;
    private static final Logger logger_ =
        Logger.getLogger( XmlRpcHubConnection.class.getName() );

    /**
     * Constructor.
     *
     * @param   xClient   XML-RPC client
     * @param   serverFactory  XML-RPC server factory implementation
     * @param   secret  samp.secret registration password
     */
    public XmlRpcHubConnection( SampXmlRpcClient xClient,
                                SampXmlRpcServerFactory serverFactory,
                                String secret )
            throws SampException {
        xClient_ = xClient;
        serverFactory_ = serverFactory;
        Object regInfo =
            rawExec( "samp.hub.register", Collections.singletonList( secret ) );
        if ( regInfo instanceof Map ) {
            regInfo_ = RegInfo.asRegInfo( Collections
                                         .unmodifiableMap( asMap( regInfo ) ) );
        }
        else {
            throw new SampException( "Bad return value from hub register method"                                   + " - not a map" );
        }
        try {
            Runtime.getRuntime().addShutdownHook( new Thread() {
                public void run() {
                    finish();
                }
            } );
        }
        catch ( SecurityException e ) {
            logger_.warning( "Can't add unregister shutdown hook: " + e );
        }
    }

    public RegInfo getRegInfo() {
        return regInfo_;
    }

    public void ping() throws SampException {
        rawExec( "samp.hub.ping", new ArrayList() );
    }

    public void setCallable( CallableClient callable ) throws SampException {
        if ( callableServer_ == null ) {
            try {
                callableServer_ = CallableClientServer
                                 .getInstance( serverFactory_.getServer() );
            }
            catch ( IOException e ) {
                throw new SampException( "Can't start client XML-RPC server",
                                         e );
            }
        }
        callableServer_.addClient( regInfo_.getPrivateKey(), callable );
        exec( "setXmlrpcCallback",
              new Object[] { callableServer_.getUrl().toString() } );
    }

    public void unregister() throws SampException {
        exec( "unregister", new Object[ 0 ] );
        if ( callableServer_ != null ) {
            callableServer_.removeClient( regInfo_.getPrivateKey() );
        }
        unregistered_ = true;
    }

    public void declareMetadata( Map meta ) throws SampException {
        exec( "declareMetadata", new Object[] { meta } );
    }

    public Metadata getMetadata( String clientId ) throws SampException {
        return Metadata
              .asMetadata( asMap( exec( "getMetadata",
                                        new Object[] { clientId } ) ) );
    }

    public void declareSubscriptions( Map subs ) throws SampException {
        exec( "declareSubscriptions", new Object[] { subs } );
    }

    public Subscriptions getSubscriptions( String clientId )
            throws SampException {
        return Subscriptions
              .asSubscriptions( asMap( exec( "getSubscriptions",
                                             new Object[] { clientId } ) ) );
    }

    public String[] getRegisteredClients() throws SampException {
        return (String[])
               asList( exec( "getRegisteredClients", new Object[ 0 ] ) )
              .toArray( new String[ 0 ] );
    }

    public Map getSubscribedClients( String mtype ) throws SampException {
        return asMap( exec( "getSubscribedClients", new Object[] { mtype } ) );
    }

    public void notify( String recipientId, Map msg ) throws SampException {
        exec( "notify", new Object[] { recipientId, msg } );
    }

    public List notifyAll( Map msg ) throws SampException {
        return asList( exec( "notifyAll", new Object[] { msg } ) );
    }

    public String call( String recipientId, String msgTag, Map msg )
            throws SampException {
        return asString( exec( "call",
                               new Object[] { recipientId, msgTag, msg } ) );
    }

    public Map callAll( String msgTag, Map msg ) throws SampException {
        return asMap( exec( "callAll", new Object[] { msgTag, msg } ) );
    }

    public Response callAndWait( String recipientId, Map msg, int timeout )
            throws SampException {
        return Response
              .asResponse(
                   asMap( exec( "callAndWait",
                                new Object[] { recipientId, msg, SampUtils
                                              .encodeInt( timeout ) } ) ) );
    }

    public void reply( String msgId, Map response ) throws SampException {
        exec( "reply", new Object[] { msgId, response } );
    }

    /**
     * Makes an XML-RPC call to the SAMP hub represented by this connection.
     *
     * @param  methodName  unqualified SAMP hub API method name
     * @param  params   array of method parameters
     * @return  XML-RPC call return value
     */
    private Object exec( String methodName, Object[] params )
            throws SampException {
        List paramList = new ArrayList();
        paramList.add( regInfo_.getPrivateKey() );
        for ( int ip = 0; ip < params.length; ip++ ) {
            paramList.add( params[ ip ] );
        }
        return rawExec( "samp.hub." + methodName, paramList );
    }

    /**
     * Actually makes an XML-RPC call to the SAMP hub represented by this
     * connection.
     *
     * @param  fqName  fully qualified SAMP hub API method name
     * @param  paramList   list of method parameters
     * @return  XML-RPC call return value
     */
    private Object rawExec( String fqName, List paramList )
            throws SampException {
        try {
            return xClient_.callAndWait( fqName, paramList );
        }
        catch ( IOException e ) {
            throw new SampException( e.getMessage(), e );
        }
    }

    /**
     * Unregisters if not already unregistered.
     * May harmlessly be called multiple times.
     */
    private void finish() {
        if ( ! unregistered_ ) {
            try {
                unregister();
            }
            catch ( SampException e ) {
            }
        }
    }

    /**
     * Unregisters if not already unregistered.
     */
    public void finalize() throws Exception {
        try {
            super.finalize();
        }
        catch ( Throwable e ) {
        }
        finish();
    }

    /**
     * Utility method to cast an object to a given SAMP-like type.
     *
     * @param  obj  object to cast
     * @param  clazz  class to cast to
     * @param   name  SAMP name of type
     * @return  obj
     * @throws  SampException  if cast attempt failed
     */
    private static Object asType( Object obj, Class clazz, String name )
            throws SampException {
        if ( clazz.isAssignableFrom( obj.getClass() ) ) {
            return obj;
        }
        else {
            throw new SampException( "Hub returned unexpected type ("
                                   + obj.getClass().getName() + " not "
                                   + name );
        }
    }

    /**
     * Utility method to cast an object to a string.
     *
     * @param  obj  object
     * @return  object as string
     * @throws  SampException  if cast attempt failed
     */
    private String asString( Object obj ) throws SampException {
        return (String) asType( obj, String.class, "string" );
    }

    /**
     * Utility method to cast an object to a list.
     *
     * @param  obj  object
     * @return  object as list
     * @throws  SampException  if cast attempt failed
     */
    private List asList( Object obj ) throws SampException {
        return (List) asType( obj, List.class, "list" );
    }

    /**
     * Utility method to cast an object to a map.
     *
     * @param  obj  object
     * @return  object as map
     * @throws  SampException  if cast attempt failed
     */
    private Map asMap( Object obj ) throws SampException {
        return (Map) asType( obj, Map.class, "map" );
    }
}
