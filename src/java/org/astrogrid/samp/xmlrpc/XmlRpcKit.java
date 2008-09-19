package org.astrogrid.samp.xmlrpc;

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
    public static XmlRpcKit APACHE;

    /** Implementation which requires no external libraries. */
    public static XmlRpcKit INTERNAL;

    static {
        try {
            APACHE =
                new ReflectionKit(
                    "apache",
                    "org.astrogrid.samp.xmlrpc.apache.ApacheClientFactory",
                    "org.astrogrid.samp.xmlrpc.apache.ApacheServerFactory" );
            INTERNAL =
                new ReflectionKit(
                    "internal",
                    "org.astrogrid.samp.xmlrpc.internal.InternalClientFactory",
                    "org.astrogrid.samp.xmlrpc.internal.InternalServerFactory"
                );
        }
        catch ( InstantiationException e ) {
            throw new AssertionError( e );
        }
        catch ( IllegalAccessException e ) {
            throw new AssertionError( e );
        }
    }

    /** Array of available known implementations of this class. */
    public static XmlRpcKit[] KNOWN_IMPLS = {
        APACHE,
        INTERNAL,
    };

    /**
     * Property which is examined to determine which implementation to use
     * by default.  For the supplied implementations should be one of
     * "<code>internal</code>" or "<code>apache</code>".
     * Alternatively, it may be the classname of a class which implements
     * {@link org.astrogrid.samp.xmlrpc.XmlRpcKit} 
     * and has a no-arg constructor.
     */
    public static final String IMPL_PROP = "jsamp.xmlrpc.impl";

    private static XmlRpcKit defaultInstance_;
    private static Logger logger_ =
        Logger.getLogger( XmlRpcKit.class.getName() );

    /**
     * Returns an XML-RPC server factory.
     *
     * @return   server factory
     */
    public abstract SampXmlRpcServerFactory getServerFactory();

    /**
     * Returns an XML-RPC client factory.
     *
     * @return  client factory
     */
    public abstract SampXmlRpcClientFactory getClientFactory();

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
     * Implementation of this class which uses reflection to instantiate
     * client and server classes.
     */
    private static class ReflectionKit extends XmlRpcKit {
        private final String name_;
        private final SampXmlRpcClientFactory xClientFactory_;
        private final SampXmlRpcServerFactory xServerFactory_;
        private final Throwable clientError_;
        private final Throwable serverError_;
        private final boolean isAvailable_;

        /**
         * Constructor.
         *
         * @param   name   implementation name
         * @param   clientFactoryClassName  name of SampXmlRpcClientFactory
         *                implementation class
         * @param   serverFactoryClassName  name of SampXmlRpcServerFactory
         *                implementation class
         */
        ReflectionKit( String name, String clientFactoryClassName,
                       String serverFactoryClassName )
                throws InstantiationException, IllegalAccessException {
            name_ = name;
            SampXmlRpcClientFactory clientFactory;
            Throwable clientError;
            try {
                clientFactory =
                    (SampXmlRpcClientFactory)
                    Class.forName( clientFactoryClassName ).newInstance();
                clientError = null;
            }
            catch ( ClassNotFoundException e ) {
                clientFactory = null;
                clientError = e;
            }
            catch ( LinkageError e ) {
                clientFactory = null;
                clientError = e;
            }
            xClientFactory_ = clientFactory;
            clientError_ = clientError;

            SampXmlRpcServerFactory serverFactory;
            Throwable serverError;
            try {
                serverFactory =
                    (SampXmlRpcServerFactory)
                    Class.forName( serverFactoryClassName ).newInstance();
                serverError = null;
            }
            catch ( ClassNotFoundException e ) {
                serverError = e;
                serverFactory = null;
            }
            catch ( LinkageError e ) {
                serverError = e;
                serverFactory = null;
            }
            xServerFactory_ = serverFactory;
            serverError_ = null;
            isAvailable_ = xClientFactory_ != null && xServerFactory_ != null;
        }

        public SampXmlRpcClientFactory getClientFactory() {
            if ( xClientFactory_ != null ) {
                return xClientFactory_;
            }
            else {
                assert clientError_ != null;
                throw new RuntimeException( name_ +
                                            " implementation not available",
                                            clientError_ );
            }
        }

        public SampXmlRpcServerFactory getServerFactory() {
            if ( xServerFactory_ != null ) {
                return xServerFactory_;
            }
            else {
                assert serverError_ != null;
                throw new RuntimeException( name_ +
                                            " implementation not available",
                                            serverError_ );
            }
        }

        public boolean isAvailable() {
            return isAvailable_;
        }

        public String getName() {
            return name_;
        }

        public String toString() {
            return getName();
        }
    }
}
