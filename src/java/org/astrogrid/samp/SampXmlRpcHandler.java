package org.astrogrid.samp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.xmlrpc.XmlRpcHandler;

public class SampXmlRpcHandler implements XmlRpcHandler {

    private final String prefix_;
    private final Object actor_;
    private final Map methodMap_;
    private final Logger logger_ =
        Logger.getLogger( SampXmlRpcHandler.class.getName() );

    public SampXmlRpcHandler( String namespace, Class actorType,
                              Object actor ) {
        prefix_ = namespace + ".";
        actor_ = actor;
        methodMap_ = new HashMap();

        Method[] methods = actorType.getDeclaredMethods();
        for ( int im = 0; im < methods.length; im++ ) {
            Method method = methods[ im ];
            if ( Modifier.isPublic( method.getModifiers() ) ) {
                String name = method.getName();
                Class[] clazzes = method.getParameterTypes();
                SampType[] types = new SampType[ clazzes.length ];
                for ( int ic = 0; ic < clazzes.length; ic++ ) {
                    types[ ic ] = SampType.getClassType( clazzes[ ic ] );
                }
                Signature sig = new Signature( prefix_ + name, types );
                methodMap_.put( sig, method );
            }
        }
    }

    public Object execute( String fqName, Vector params )
            throws SampException {
        logger_.info( fqName + params );
        try {
            return doExecute( fqName, params );
        }
        catch ( SampException e ) {
            logger_.warning( e.getMessage() );
            throw e;
        }
        catch ( RuntimeException e ) {
            logger_.warning( e.getMessage() );
            throw e;
        }
        catch ( Error e ) {
            logger_.log( Level.WARNING, e.getMessage(), e );
            throw e;
        }
    }

    private Object doExecute( String fqName, Vector params )
            throws SampException {
        if ( fqName.startsWith( prefix_ ) ) {
            String name = fqName.substring( prefix_.length() );
            List typeList = new ArrayList();
            for ( Iterator it = params.iterator(); it.hasNext(); ) {
                typeList.add( SampType.getParamType( it.next() ) );
            }
            SampType[] types =
                (SampType[]) typeList.toArray( new SampType[ 0 ] );
            Signature sig = new Signature( fqName, types );
            Method method = (Method) methodMap_.get( sig );
            if ( method != null ) {
                Object result;
                Throwable error;
                try {
                    result = method.invoke( actor_, params.toArray() );
                }
                catch ( InvocationTargetException e ) {
                    Throwable e2 = e.getCause();
                    throw e2 instanceof SampException
                        ? (SampException) e2
                        : new SampException( "Error processing " + name, e2 );
                }
                catch ( Throwable e ) {
                    throw new SampException( e );
                }
                return result == null ? ""
                                      : toApache( result );
            }
            else {
                for ( Iterator it = methodMap_.keySet().iterator();
                      it.hasNext(); ) {
                    Signature foundSig = (Signature) it.next();
                    if ( foundSig.name_.equals( fqName ) ) {
                        throw new IllegalArgumentException(
                                "Bad arguments: " + foundSig + " got " 
                              + sig.typeList_ );
                    }
                }
                throw new UnsupportedOperationException( "Unknown method "
                                                       + fqName );
            }
        }
        else {
            throw new UnsupportedOperationException(
                          "Unrecognized method " + fqName
                        + " does not have prefix " + prefix_ );
        }
    }

    public Object getActor() {
        return actor_;
    }

    public static Object toApache( Object obj ) {
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

    private static class SampType {
        public static final SampType STRING =
            new SampType( String.class, "string" );
        public static final SampType LIST =
            new SampType( List.class, "list" );
        public static final SampType MAP =
            new SampType( Map.class, "map" );

        private final Class clazz_;
        private final String name_;

        public SampType( Class clazz, String name ) {
            clazz_ = clazz;
            name_ = name;
        }

        public Class getTypeClass() {
            return clazz_;
        }

        public String toString() {
            return name_;
        }

        public static SampType getClassType( Class clazz ) {
            if ( String.class.equals( clazz ) ) {
                return STRING;
            }
            else if ( List.class.equals( clazz ) ) {
                return LIST;
            }
            else if ( Map.class.equals( clazz ) ) {
                return MAP;
            }
            else {
                throw new IllegalArgumentException( "Illegal type "
                                                  + clazz.getName() );
            }
        }
        public static SampType getParamType( Object param )
                throws SampException {
            if ( param instanceof String ) {
                return STRING;
            }
            else if ( param instanceof List ) {
                return LIST;
            }
            else if ( param instanceof Map ) {
                return MAP;
            }
            else {
                throw new SampException( "Param is not a SAMP type" );
            }
        }
    }

    private static class Signature {
        private final String name_;
        private final List typeList_;

        Signature( String name, SampType[] types ) {
            name_ = name;
            typeList_ = new ArrayList( Arrays.asList( types ) );
        }

        public boolean equals( Object o ) {
            if ( o instanceof Signature ) {
                Signature other = (Signature) o;
                return this.name_.equals( other.name_ )
                    && this.typeList_.equals( other.typeList_ );
            }
            else {
                return false;
            }
        }

        public int hashCode() {
            int code = 999;
            code = 23 * code + name_.hashCode();
            code = 23 * code + typeList_.hashCode();
            return code;
        }

        public String toString() {
            return name_ + typeList_;
        }
    }
}
