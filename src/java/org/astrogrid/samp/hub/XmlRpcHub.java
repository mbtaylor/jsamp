package org.astrogrid.samp.hub;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.apache.xmlrpc.XmlRpcHandler;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;

public class XmlRpcHub implements XmlRpcHandler {

    private final HubService service_;
    private final Logger logger_ = 
        Logger.getLogger( XmlRpcHandler.class.getName() );

    public final static String HUB_PREFIX = "samp.hub.";

    public XmlRpcHub( HubService service ) {
        service_ = service;
    }

    public Object execute( String method, Vector params ) throws SampException {
        if ( method.startsWith( HUB_PREFIX ) ) {
            String hubMethod = method.substring( HUB_PREFIX.length() );
            ParamList paramList = new ParamList( params );
            Object result = toApache( doExecute( hubMethod, paramList ) );
            if ( ! paramList.isEmpty() ) {
                logger_.warning( "Additional arguments unused" );
            }
            return result;
        }
        else {
            throw new UnsupportedOperationException(
                method + " not hub method - does not start " + HUB_PREFIX );
        }
    }

    public Object doExecute( String method, ParamList paramList )
            throws SampException {
        if ( "register".equals( method ) ) {
            return service_.register( paramList.shiftString() );
        }
        else if ( "isAlive".equals( method ) ) {
            paramList.clear();
            return "";
        }

        String privateKey = paramList.shiftString();
        if ( "unregister".equals( method ) ) {
            service_.unregister( privateKey );
            return "";
        }
        else if ( "declareMetadata".equals( method ) ) {
            service_.declareMetadata( privateKey,
                                      paramList.shiftMap() );
            return "";
        }
        else if ( "getMetadata".equals( method ) ) {
            return service_.getMetadata( privateKey,
                                         paramList.shiftString() );
        }
        else if ( "declareSubscriptions".equals( method ) ) {
            service_.declareSubscriptions( privateKey,
                                           paramList.shiftMap() );
            return "";
        }
        else if ( "getSubscriptions".equals( method ) ) {
            return service_.getSubscriptions( privateKey,
                                              paramList.shiftString() );
        }
        else if ( "getRegisteredClients".equals( method ) ) {
            return service_.getRegisteredClients( privateKey );
        }
        else if ( "getSubscribedClients".equals( method ) ) {
            return service_.getSubscribedClients( privateKey,
                                                  paramList.shiftString() );
        }
        else if ( "notify".equals( method ) ) {
            service_.notify( privateKey,
                             paramList.shiftString(),
                             paramList.shiftMap() );
            return "";
        }
        else if ( "notifyAll".equals( method ) ) {
            service_.notifyAll( privateKey,
                                paramList.shiftMap() );
            return "";
        }
        else if ( "call".equals( method ) ) {
            service_.call( privateKey,
                           paramList.shiftString(),
                           paramList.shiftString(),
                           paramList.shiftMap() );
            return "";
        }
        else if ( "callAll".equals( method ) ) {
            service_.callAll( privateKey,
                              paramList.shiftString(),
                              paramList.shiftMap() );
            return "";
        }
        else if ( "callAndWait".equals( method ) ) {
            return service_.callAndWait( privateKey,
                                         paramList.shiftString(),
                                         paramList.shiftMap(),
                                         paramList.shiftString() );
        }
        else if ( "reply".equals( method ) ) {
            service_.reply( privateKey,
                            paramList.shiftString(), 
                            paramList.shiftMap() );
            return "";
        }
        else {
            throw new UnsupportedOperationException( "No method " + method
                                                   + " on hub" );
        }
    }

    private static Object toApache( Object obj ) {
        SampUtils.checkObject( obj );
        if ( obj instanceof List ) {
            Vector vec = new Vector();
            for ( Iterator it = ((List) obj).iterator(); it.hasNext(); ) {
                vec.add( toApache( it.next() ) );
            }
            return vec;
        }
        else if ( obj instanceof Map ) {
            Hashtable hash = new Hashtable();
            for ( Iterator it = ((Map) obj).entrySet().iterator();
                  it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                hash.put( entry.getKey(), toApache( entry.getValue() ) );
            }
            return hash;
        }
        else if ( obj instanceof String ) {
            return obj;
        }
        else {
            assert false : "But I checked!";
            throw new DataException( "Unexpected object type "
                                   + ( obj == null
                                           ? null 
                                           : obj.getClass().getName() ) );
        }
    }

    private static class ParamList extends ArrayList {

        public ParamList( List paramList ) {
            super( paramList );
        }

        public String shiftString() throws SampException {
            return (String) shiftTyped( String.class, "string" );
        }

        public Map shiftMap() throws SampException {
            return (Map) shiftTyped( Map.class, "map" );
        }

        public List shiftList() throws SampException {
            return (List) shiftTyped( List.class, "list" );
        }

        private Object shiftTyped( Class clazz, String cname )
                throws SampException {
            if ( isEmpty() ) {
                throw new SampException( "Too few parameters supplied" );
            }
            else if ( ! clazz.isAssignableFrom( get( 0 ).getClass() ) ) {
                throw new SampException( "Parameter of wrong type"
                                       + " (should be " + cname + ")" );
            }
            else {
                return remove( 0 );
            }
        }
    }
}
