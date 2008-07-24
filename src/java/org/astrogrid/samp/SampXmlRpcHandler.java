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

/**
 * Utility class to facilitate constructing an XmlRpcHandler which handles
 * particular named methods.
 * You supply at construction time an interface which defines the methods
 * to be handled and an object which implements that interface.
 * This object then uses reflection to invoke the correct methods on the
 * implementation object as they are required from incoming XML-RPC
 * <code>execute</code> requests.  This insulates the implementation object
 * from having to worry about any XML-RPC, or Apache XML-RPC, specifics.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class SampXmlRpcHandler implements XmlRpcHandler {

    private final String prefix_;
    private final Object actor_;
    private final Map methodMap_;
    private final Logger logger_ =
        Logger.getLogger( SampXmlRpcHandler.class.getName() );

    /**
     * Constructor.
     * Note that because of java rules about member visibility,
     * <code>actorType</code> <em>must</em> be visible from this class,
     * that is it may not be package-private to another package.
     *
     * @param  namespace  string prepended to every method name in the 
     *         <code>actorType</code> interface to form the XML-RPC
     *         <code>methodName</code> element
     * @param  actorType  interface defining the XML-RPC methods
     * @param  actor     object implementing <code>actorType</code>
     */
    public SampXmlRpcHandler( String namespace, Class actorType,
                              Object actor ) {
        prefix_ = namespace + ".";
        actor_ = actor;
        methodMap_ = new HashMap();

        // Construct a map keyed by method signature of the known methods.
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

    /**
     * This method is called by the XML-RPC server for incoming method
     * requests.
     *
     * @param  fqName  fully qualified XML-RPC methodName element
     * @param  params  method parameters
     */
    public Object execute( String fqName, Vector params ) throws Exception {
        logger_.info( fqName + params );
        try {
            return doExecute( fqName, params );
        }
        catch ( Throwable e ) {
            logger_.log( Level.WARNING, e.getMessage(), e );
            if ( e instanceof Error ) {
                throw (Error) e;
            }
            else {
                throw (Exception) e;
            }
        }
    }

    /**
     * Does the work for invoking the relevant method on the actor 
     * for a given incoming method call.
     *
     * @param  fqName  fully qualified XML-RPC methodName element
     * @param  params  method parameters
     */
    private Object doExecute( String fqName, Vector params ) throws Exception {

        // May be for us.
        if ( fqName.startsWith( prefix_ ) ) {

            // Work out the signature for this method and see if it is 
            // recognised.
            String name = fqName.substring( prefix_.length() );
            List typeList = new ArrayList();
            for ( Iterator it = params.iterator(); it.hasNext(); ) {
                typeList.add( SampType.getParamType( it.next() ) );
            }
            SampType[] types =
                (SampType[]) typeList.toArray( new SampType[ 0 ] );
            Signature sig = new Signature( fqName, types );
            Method method = (Method) methodMap_.get( sig );

            // If the signature is recognised, invoke the relevant method
            // on the implementation object.
            if ( method != null ) {
                Object result;
                Throwable error;
                try {
                    result = method.invoke( actor_, params.toArray() );
                }
                catch ( InvocationTargetException e ) {
                    Throwable e2 = e.getCause();
                    if ( e2 instanceof Error ) {
                        throw (Error) e2;
                    }
                    else {
                        throw (Exception) e2;
                    }
                }
                catch ( Exception e ) {
                    throw e;
                }
                catch ( Error e ) {
                    throw e;
                }
                return result == null ? ""
                                      : toApache( result );
            }

            // If the signature is not recognised, but the method name is,
            // try to make a helpful comment.
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

        // Not for us.
        else {
            throw new UnsupportedOperationException(
                          "Unrecognized method " + fqName
                        + " does not have prefix " + prefix_ );
        }
    }

    /**
     * Returns the implementation object for this handler.
     *
     * @return   implementaion object
     */
    public Object getActor() {
        return actor_;
    }

    /**
     * Converts an object from 'normal' XML-RPC form to Apache XML-RPC form.
     * Basically, this means converting
     * {@link java.util.Map}s to {@link java.util.Hashtable}s and
     * {@link java.util.List}s to {@link java.util.Vector}s.
     *
     * @param  obj  object suitable for generic use within XML-RPC
     * @return  object suitable for transmission using Apache XML-RPC library
     */
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

    /**
     * Enumeration of permitted types within a SAMP data structure.
     */
    private static class SampType {

        /** String type. */
        public static final SampType STRING =
            new SampType( String.class, "string" );

        /** List type. */
        public static final SampType LIST =
            new SampType( List.class, "list" );

        /** Map type. */
        public static final SampType MAP =
            new SampType( Map.class, "map" );

        private final Class clazz_;
        private final String name_;

        /**
         * Constructor.
         *
         * @param  clazz  java class
         * @param  name  name of SAMP type
         */
        private SampType( Class clazz, String name ) {
            clazz_ = clazz;
            name_ = name;
        }

        /**
         * Returns the java class corresponding to this type.
         *
         * @return  class
         */
        public Class getTypeClass() {
            return clazz_;
        }

        /**
         * Returns the SAMP name for this type.
         *
         * @return  name
         */
        public String toString() {
            return name_;
        }

        /**
         * Returns the SampType corresponding to a given java class.
         *
         * @param  clazz  class
         * @return  SAMP type
         */
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

        /**
         * Returns the SampType corresponding to a given object.
         *
         * @param  param   object
         * return  SAMP type
         */
        public static SampType getParamType( Object param ) {
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
                throw new DataException( "Param is not a SAMP type" );
            }
        }
    }

    /**
     * Characterises a method signature.
     * The <code>equals</code> and <code>hashCode</code> methods are 
     * implemented sensibly.
     */
    private static class Signature {
        private final String name_;
        private final List typeList_;

        /**
         * Constructor.
         *
         * @param  name   method name
         * @param  types  types of method arguments
         */
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
