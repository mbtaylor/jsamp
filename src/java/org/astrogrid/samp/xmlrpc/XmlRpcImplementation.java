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
public abstract class XmlRpcImplementation {

    /** Implementation based on Apache XML-RPC. */
    public static XmlRpcImplementation APACHE;

    /** Implementation which requires no external libraries. */
    public static XmlRpcImplementation INTERNAL;

    static {
        try {
            APACHE =
                new ReflectionImplementation(
                    "apache",
                    "org.astrogrid.samp.xmlrpc.apache.ApacheClient",
                    "org.astrogrid.samp.xmlrpc.apache.ApacheServerFactory" );
            INTERNAL =
                new ReflectionImplementation(
                    "internal",
                    "org.astrogrid.samp.xmlrpc.internal.InternalClient",
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
    public static XmlRpcImplementation[] KNOWN_IMPLS = {
        APACHE,
        INTERNAL,
    };

    /**
     * Property which is examined to determine which implementation to use
     * by default.  For the supplied implementations should be one of
     * "internal" or "apache".
     */
    public static final String IMPL_PROP = "jsamp.xmlrpc.impl";

    private static XmlRpcImplementation defaultInstance_;
    private static Logger logger_ =
        Logger.getLogger( XmlRpcImplementation.class.getName() );

    /**
     * Returns an XML-RPC server factory.
     *
     * @return   server factory
     */
    public abstract SampXmlRpcServerFactory getServerFactory();

    /**
     * Returns an XML-RPC client.
     *
     * @return  client
     */
    public abstract SampXmlRpcClient getClient();

    /**
     * Indicates whether this object is ready for use.
     * If it returns false (perhaps because some classes are unavailable
     * at runtime) then {@link #getClient} and {@link #getServerFactory}
     * may throw exceptions rather than behaving as documented.
     *
     * @return   true if this object works
     */
    public abstract boolean isAvailable();

    /**
     * Returns the name of this implementation.
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
    public static XmlRpcImplementation getInstance() {
        if ( defaultInstance_ == null ) {
            defaultInstance_ = createDefaultInstance();
            logger_.info( "Default XmlRpcInstance is " + defaultInstance_ );
        }
        return defaultInstance_;
    }

    /** 
     * Returns an XmlRpcImplementation instance given its name.
     *
     * @param   name   name of one of the known implementations, or classname
     *          of an XmlRpcImplementation implemenatation with a no-arg
     *          constructor
     * @return  named implementation object
     * @throws  IllegalArgumentException  if none by that name can be found
     */
    public static XmlRpcImplementation getInstanceByName( String name ) {

        // Implementation specified by system property -
        // try to find one with a matching name in the known list.
        XmlRpcImplementation[] impls = KNOWN_IMPLS;
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
            return (XmlRpcImplementation) clazz.newInstance();
        }
        catch ( Throwable e ) {
            throw (RuntimeException)
                  new IllegalArgumentException( "Error instantiating custom "
                                              + "XmlRpcImplementation "
                                              + clazz.getName() )
                 .initCause( e );
        }
    }

    /**
     * Constructs the default instance of this class based on system property
     * and class availability.
     *
     * @return   implementation object
     * @see      #getInstance
     */
    private static XmlRpcImplementation createDefaultInstance() {
        XmlRpcImplementation[] impls = KNOWN_IMPLS;
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
    private static class ReflectionImplementation extends XmlRpcImplementation {
        private final String name_;
        private final SampXmlRpcClient xClient_;
        private final SampXmlRpcServerFactory xServerFactory_;
        private final Throwable clientError_;
        private final Throwable serverError_;
        private final boolean isAvailable_;

        /**
         * Constructor.
         *
         * @param   name   implementation name
         * @param   clientClassName  name of SampXmlRpcClient 
         *                implementation class
         * @param   serverFactoryClassName  name of SampXmlRpcServerFactory
         *                implementation class
         */
        ReflectionImplementation( String name,
                                  String clientClassName,
                                  String serverFactoryClassName )
                throws InstantiationException, IllegalAccessException {
            name_ = name;
            SampXmlRpcClient client;
            Throwable clientError;
            try {
                client =
                    (SampXmlRpcClient)
                    Class.forName( clientClassName ).newInstance();
                clientError = null;
            }
            catch ( ClassNotFoundException e ) {
                client = null;
                clientError = e;
            }
            catch ( LinkageError e ) {
                client = null;
                clientError = e;
            }
            xClient_ = client;
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
            isAvailable_ = xClient_ != null && xServerFactory_ != null;
        }

        public SampXmlRpcClient getClient() {
            if ( xClient_ != null ) {
                return xClient_;
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
