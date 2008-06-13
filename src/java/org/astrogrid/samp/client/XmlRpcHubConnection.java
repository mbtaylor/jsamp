package org.astrogrid.samp.client;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.SampXmlRpcHandler;
import org.astrogrid.samp.Subscriptions;

public class XmlRpcHubConnection implements HubConnection {

    private final XmlRpcClient xClient_;
    private final RegInfo regInfo_;
    private CallableClientServer callableServer_;
    private boolean unregistered_;

    public XmlRpcHubConnection( URL hubUrl, String secret )
            throws SampException {
        xClient_ = new XmlRpcClientLite( hubUrl );
        Object regInfo =
            rawExec( "samp.hub.register", 
                     new Vector( Collections.singleton( secret ) ) );
        if ( regInfo instanceof Map ) {
            regInfo_ = RegInfo.asRegInfo( Collections
                                         .unmodifiableMap( asMap( regInfo ) ) );
        }
        else {
            throw new SampException( "Bad return value from hub register method"                                   + " - not a map" );
        }
        Runtime.getRuntime().addShutdownHook( new Thread() {
            public void run() {
                finish();
            }
        } );
    }

    public RegInfo getRegInfo() {
        return regInfo_;
    }

    public void ping() throws SampException {
        rawExec( "samp.hub.ping", new Vector() );
    }

    public void setCallable( CallableClient callable ) throws SampException {
        if ( callableServer_ == null ) {
            try {
                callableServer_ = CallableClientServer.getInstance();
            }
            catch ( IOException e ) {
                throw new SampException( e );
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

    public void notifyAll( Map msg ) throws SampException {
        exec( "notifyAll", new Object[] { msg } );
    }

    public String call( String recipientId, String msgTag, Map msg )
            throws SampException {
        return asString( exec( "call",
                               new Object[] { recipientId, msgTag, msg } ) );
    }

    public String callAll( String msgTag, Map msg ) throws SampException {
        return asString( exec( "callAll", new Object[] { msgTag, msg } ) );
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

    private Object exec( String methodName, Object[] params )
            throws SampException {
        Vector paramVec = new Vector();
        paramVec.add( regInfo_.getPrivateKey() );
        for ( int ip = 0; ip < params.length; ip++ ) {
            paramVec.add( SampXmlRpcHandler.toApache( params[ ip ] ) );
        }
        return rawExec( "samp.hub." + methodName, paramVec );
    }

    private Object rawExec( String fqName, Vector paramVec )
            throws SampException {
        try {
            return xClient_.execute( fqName, paramVec );
        }
        catch ( XmlRpcException e ) {
            throw new SampException( e.getMessage(), e );
        }
        catch ( IOException e ) {
            throw new SampException( e.getMessage(), e );
        }
    }

    private void finish() {
        if ( ! unregistered_ ) {
            try {
                unregister();
            }
            catch ( SampException e ) {
            }
        }
    }

    public void finalize() throws Exception {
        try {
            super.finalize();
        }
        catch ( Throwable e ) {
        }
        finish();
    }

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

    private String asString( Object obj ) throws SampException {
        return (String) asType( obj, String.class, "string" );
    }

    private List asList( Object obj ) throws SampException {
        return (List) asType( obj, List.class, "list" );
    }

    private Map asMap( Object obj ) throws SampException {
        return (Map) asType( obj, Map.class, "map" );
    }
}
