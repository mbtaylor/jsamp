package org.astrogrid.samp.xmlrpc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

/**
 * Encapsulates the provision of XML-RPC client and server capabilities.
 * Two implementations are provided in the JSAMP package;
 * the pluggable architecture allows others to be provided.
 *
 * @author   Mark Taylor
 * @since    27 Aug 2008
 */
public abstract class XmlRpcKit {

    /** Implementation based on Apache XML-RPC. */
    public static final XmlRpcKit APACHE;

    /** Implementation which requires no external libraries. */
    public static final XmlRpcKit INTERNAL;

    /** Internal implementation variant with verbose logging of XML I/O. */
    public static final XmlRpcKit XML_LOGGING;

    /** Internal implementation variant with verbose logging of RPC calls. */
    public static final XmlRpcKit RPC_LOGGING;

    /** Array of available known implementations of this class. */
    public static XmlRpcKit[] KNOWN_IMPLS = {
        INTERNAL = createReflectionKit(
            "internal",
            "org.astrogrid.samp.xmlrpc.internal.InternalClientFactory",
            "org.astrogrid.samp.xmlrpc.internal.InternalServerFactory" ),
        XML_LOGGING = createReflectionKit(
            "xml-log",
            "org.astrogrid.samp.xmlrpc.internal"
                       + ".XmlLoggingInternalClientFactory",
            "org.astrogrid.samp.xmlrpc.internal"
                       + ".XmlLoggingInternalServerFactory" ),
        RPC_LOGGING = createReflectionKit(
            "rpc-log",
            "org.astrogrid.samp.xmlrpc.internal"
                       + ".RpcLoggingInternalClientFactory",
            "org.astrogrid.samp.xmlrpc.internal"
                       + ".RpcLoggingInternalServerFactory" ),
        APACHE = createApacheKit( "apache" ),
    };

    /**
     * Property which is examined to determine which implementation to use
     * by default.  Property values may be one of the elements of 
     * {@link #KNOWN_IMPLS}, currently:
     * <ul>
     * <li>internal</li>
     * <li>xml-log</li>
     * <li>rpc-log</li>
     * <li>apache</li>
     * </ul>
     * Alternatively, it may be the classname of a class which implements
     * {@link org.astrogrid.samp.xmlrpc.XmlRpcKit} 
     * and has a no-arg constructor.
     * The property name is "<code>{@value}</code>".
     */
    public static final String IMPL_PROP = "jsamp.xmlrpc.impl";

    private static XmlRpcKit defaultInstance_;
    private static Logger logger_ =
        Logger.getLogger( XmlRpcKit.class.getName() );

    /**
     * Returns an XML-RPC client factory.
     *
     * @return  client factory
     */
    public abstract SampXmlRpcClientFactory getClientFactory();

    /**
     * Returns an XML-RPC server factory.
     *
     * @return   server factory
     */
    public abstract SampXmlRpcServerFactory getServerFactory();

    /**
     * Indicates whether this object is ready for use.
     * If it returns false (perhaps because some classes are unavailable
     * at runtime) then {@link #getClientFactory} and {@link #getServerFactory}
     * may throw exceptions rather than behaving as documented.
     *
     * @return   true if this object works
     */
    public abstract boolean isAvailable();

    /**
     * Returns the name of this kit.
     *
     * @return  implementation name
     */
    public abstract String getName();

    /**
     * Returns the default instance of this class.
     * What implementation this is normally depends on what classes 
     * are present at runtime. 
     * However, if the system property {@link #IMPL_PROP} is set this
     * will determine the implementation used.  It may be one of:
     * <ul>
     * <li><code>apache</code>: implementation based on the 
     *     Apache XML-RPC library</li>
     * <li><code>internal</code>: implementation which requires no libraries
     *     beyond JSAMP itself</li>
     * <li>the classname of an implementation of this class which has a 
     *     no-arg constructor</li>
     * </ul>
     *
     * @return  default instance of this class
     */
    public static XmlRpcKit getInstance() {
        if ( defaultInstance_ == null ) {
            defaultInstance_ = createDefaultInstance();
            logger_.info( "Default XmlRpcInstance is " + defaultInstance_ );
        }
        return defaultInstance_;
    }

    /** 
     * Returns an XmlRpcKit instance given its name.
     *
     * @param   name   name of one of the known implementations, or classname
     *          of an XmlRpcKit implementatation with a no-arg
     *          constructor
     * @return  named implementation object
     * @throws  IllegalArgumentException  if none by that name can be found
     */
    public static XmlRpcKit getInstanceByName( String name ) {

        // Implementation specified by system property -
        // try to find one with a matching name in the known list.
        XmlRpcKit[] impls = KNOWN_IMPLS;
        for ( int i = 0; i < impls.length; i++ ) {
            if ( name.equalsIgnoreCase( impls[ i ].getName() ) ) {
                return impls[ i ];
            }
        }

        // Still not got one -
        // try to interpret system property as class name.
        Class clazz;
        try {
            clazz = Class.forName( name );
        }
        catch ( ClassNotFoundException e ) {
            throw new IllegalArgumentException( "No such XML-RPC "
                                              + "implementation \""
                                              + name + "\"" );
        }
        try {
            return (XmlRpcKit) clazz.newInstance();
        }
        catch ( Throwable e ) {
            throw (RuntimeException)
                  new IllegalArgumentException( "Error instantiating custom "
                                              + "XmlRpcKit "
                                              + clazz.getName() )
                 .initCause( e );
        }
    }

    /**
     * Constructs the default instance of this class based on system property
     * and class availability.
     *
     * @return   XmlRpcKit object
     * @see      #getInstance
     */
    private static XmlRpcKit createDefaultInstance() {
        XmlRpcKit[] impls = KNOWN_IMPLS;
        String implName = System.getProperty( IMPL_PROP );
        logger_.info( "Creating default XmlRpcInstance: " + IMPL_PROP + "=" +
                      implName );

        // No implementation specified by system property -
        // use the first one in the list that works.
        if ( implName == null ) {
            for ( int i = 0; i < impls.length; i++ ) {
                if ( impls[ i ].isAvailable() ) {
                    return impls[ i ];
                }
            }
            return impls[ 0 ];
        }

        // Implementation specified by system property -
        // try to find one with a matching name in the known list.
        else {
            return getInstanceByName( implName );
        }
    }

    /**
     * Returns a new XmlRpcKit given classnames for the client and server
     * factory classes.  If the classes are not available, a kit which
     * returns {@link #isAvailable}()=false will be returned.
     *
     * @param  name  kit name
     * @param  clientFactoryClassName  name of class implementing
     *            SampXmlRpcClientFactory which has a no-arg constructor
     * @param  serverFactoryClassName  name of class implementing
     *            SampXmlRpcServerFactory which has a no-arg constructor
     * @return  new XmlRpcKit constructed using reflection
     */
    public static XmlRpcKit createReflectionKit( String name,
                                                 String clientFactoryClassName,
                                                 String serverFactoryClassName
                                                 ) {
        SampXmlRpcClientFactory clientFactory = null;
        SampXmlRpcServerFactory serverFactory = null;
        Throwable error = null;
        try {
            clientFactory = (SampXmlRpcClientFactory)
                            Class.forName( clientFactoryClassName )
                                 .newInstance();
            serverFactory = (SampXmlRpcServerFactory)
                            Class.forName( serverFactoryClassName )
                                 .newInstance();
        }
        catch ( ClassNotFoundException e ) {
            error = e;
        }
        catch ( LinkageError e ) {
            error = e;
        }
        catch ( InstantiationException e ) {
            error = e;
        }
        catch ( IllegalAccessException e ) {
            error = e;
        }
        if ( clientFactory != null && serverFactory != null ) {
            assert error == null;
            return new AvailableKit( name, clientFactory, serverFactory );
        }
        else {
            assert error != null;
            return new UnavailableKit( name, error );
        }
    }

    /**
     * XmlRpcKit implementation which is available.
     */
    private static class AvailableKit extends XmlRpcKit {
        private final String name_;
        private final SampXmlRpcClientFactory clientFactory_;
        private final SampXmlRpcServerFactory serverFactory_;

        /**
         * Constructor.
         *
         * @param   name   implementation name
         * @param   clientFactory  SampXmlRpcClientFactory instance
         * @param   serverFactory  SampXmlRpcServerFactory instance
         */
        AvailableKit( String name,
                      SampXmlRpcClientFactory clientFactory,
                      SampXmlRpcServerFactory serverFactory ) {
            name_ = name;
            clientFactory_ = clientFactory;
            serverFactory_ = serverFactory;
        }

        public SampXmlRpcClientFactory getClientFactory() {
            return clientFactory_;
        }

        public SampXmlRpcServerFactory getServerFactory() {
            return serverFactory_;
        }

        public String getName() {
            return name_;
        }

        public boolean isAvailable() {
            return true;
        }

        public String toString() {
            return name_;
        }
    }

    /**
     * XmlRpcKit implementation which always returns false from isAvailable
     * and throws exceptions from getServer/Client factory methods.
     */
    private static class UnavailableKit extends XmlRpcKit {
        private final String name_;
        private final Throwable error_;

        /**
         * Constructor.
         *
         * @param  kit name
         * @param  error  the reason the kit is unavailable
         */
        UnavailableKit( String name, Throwable error ) {
            name_ = name;
            error_ = error;
        }

        public SampXmlRpcClientFactory getClientFactory() {
            throw (RuntimeException)
                  new UnsupportedOperationException( name_
                                                   + " implementation not"
                                                   + " available" )
                 .initCause( error_ );
        }

        public SampXmlRpcServerFactory getServerFactory() {
            throw (RuntimeException)
                  new UnsupportedOperationException( name_
                                                   + " implementation not"
                                                   + " available" )
                 .initCause( error_ );
        }

        public String getName() {
            return name_;
        }

        public boolean isAvailable() {
            return false;
        }

        public String toString() {
            return name_;
        }
    }

    /**
     * Returns an available or unavailable XmlRpcKit based on Apache XML-RPC
     * version 1.2.
     *
     * @param  name  kit name
     * @return   new kit
     */
    private static XmlRpcKit createApacheKit( String name ) {
        XmlRpcKit kit = createReflectionKit(
            name,
            "org.astrogrid.samp.xmlrpc.apache.ApacheClientFactory",
            "org.astrogrid.samp.xmlrpc.apache.ApacheServerFactory" );
        if ( kit.isAvailable() ) {
            try {
                Class xClazz = Class.forName( "org.apache.xmlrpc.XmlRpc" );
                Field vField = xClazz.getField( "version" );
                Object version = Modifier.isStatic( vField.getModifiers() )
                               ? vField.get( null )
                               : null;
                if ( version instanceof String
                     && ((String) version)
                       .startsWith( "Apache XML-RPC 1.2" ) ) {
                    return kit;
                }
                else {
                    String msg = "Wrong Apache XML-RPC version: " + version
                               + " not 1.2";
                    return
                        new UnavailableKit( name,
                                            new ClassNotFoundException( msg ) );
                }
            }
            catch ( Throwable e ) {
                return new UnavailableKit( name, e );
            }
        }
        else {
            return kit;
        }
    }
}
