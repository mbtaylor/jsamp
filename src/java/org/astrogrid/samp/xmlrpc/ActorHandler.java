package org.astrogrid.samp.xmlrpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.astrogrid.samp.DataException;

/**
 * Utility class to facilitate constructing a SampXmlRpcHandler which handles
 * particular named methods.
 * You supply at construction time an interface which defines the methods
 * to be handled and an object which implements that interface.
 * This object then uses reflection to invoke the correct methods on the
 * implementation object as they are required from incoming XML-RPC
 * <code>execute</code> requests.  This insulates the implementation object
 * from having to worry about any XML-RPC specifics.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public abstract class ActorHandler implements SampXmlRpcHandler {

    private final String prefix_;
    private final Object actor_;
    private final Map methodMap_;
    private final Logger logger_ =
        Logger.getLogger( ActorHandler.class.getName() );

    /**
     * Constructor.
     *
     * @param  prefix  string prepended to every method name in the
     *         <code>actorType</code> interface to form the XML-RPC
     *         <code>methodName</code> element
     * @param  actorType  interface defining the XML-RPC methods
     * @param  actor     object implementing <code>actorType</code>
     */
    public ActorHandler( String prefix, Class actorType, Object actor ) {
        prefix_ = prefix;
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

    public boolean canHandleCall( String fqName ) {
        return fqName.startsWith( prefix_ );
    }

    public Object handleCall( String fqName, List params, Object reqInfo )
            throws Exception {
        if ( ! canHandleCall( fqName ) ) {
            throw new IllegalArgumentException( "No I can't" );
        }

        // Work out the signature for this method and see if it is recognised.
        String name = fqName.substring( prefix_.length() );
        List typeList = new ArrayList();
        for ( Iterator it = params.iterator(); it.hasNext(); ) {
            typeList.add( SampType.getParamType( it.next() ) );
        }
        SampType[] types = (SampType[]) typeList.toArray( new SampType[ 0 ] );
        Signature sig = new Signature( fqName, types );
        Method method = (Method) methodMap_.get( sig );

        // If the signature is recognised, invoke the relevant method
        // on the implementation object.
        if ( method != null ) {
            Object result;
            Throwable error;
            try {
                result = invokeMethod( method, actor_, params.toArray() );
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
            return result == null ? "" : result;
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

    /**
     * Returns the implementation object for this handler.
     *
     * @return   implementation object
     */
    public Object getActor() {
        return actor_;
    }

    /**
     * Invokes a method reflectively on an object.
     * This method should be implemented in the obvious way, that is
     * <code>return method.invoke(obj,params)</code>.
     *
     * <p>If the implementation is effectively prescribed, why is this
     * abstract method here?  It's tricky.
     * The reason is so that reflective method invocation from this class
     * is done by code within the actor implementation class itself
     * rather than by code in the superclass, <code>ActorHandler</code>.
     * That in turn means that the <code>actorType</code> class specified
     * in the constructor does not need to be visible from 
     * <code>ActorHandler</code>'s package, only from the package where
     * the implementation class lives.
     *
     * @param  method  method to invoke
     * @param  obj   object to invoke the method on
     * @param  args   arguments for the method call
     * @see   java.lang.reflect.Method#invoke
     */
    protected abstract Object invokeMethod( Method method, Object obj,
                                            Object[] args )
            throws IllegalAccessException, InvocationTargetException;

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
