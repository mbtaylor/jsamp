package org.astrogrid.samp.client;

import java.util.logging.Logger;
import org.astrogrid.samp.Platform;
import org.astrogrid.samp.web.WebClientProfile;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;

/**
 * Factory which supplies the default ClientProfile for use by SAMP clients.
 * By using this class to obtain ClientProfile instances, applications 
 * can be used with non-standard profiles supplied at runtime without
 * requiring any code changes.
 *
 * <p>The profile returned by this class depends on the SAMP_HUB environment
 * variable ({@link #HUBLOC_ENV}).
 * If it consists of the prefix "<code>jsamp-class:</code>" 
 * ({@link #HUBLOC_CLASS_PREFIX}) followed by the classname of a class
 * which implements {@link ClientProfile} and has a no-arg constructor,
 * then an instance of the named class is used.
 * Otherwise, an instance of {@link StandardClientProfile} or 
 * {@link WebClientProfile} is returned.
 *
 * @author   Mark Taylor
 * @since    4 Aug 2009
 */
public class DefaultClientProfile {

    private static ClientProfile profile_;
    private static final Logger logger_ =
        Logger.getLogger( DefaultClientProfile.class.getName() );

    /** Environment variable used for hub location ({@value}). */
    public static final String HUBLOC_ENV = "SAMP_HUB";

    /**
     * Prefix for SAMP_HUB env var indicating a supplied ClientProfile
     * implementation ({@value}).
     */
    public static final String HUBLOC_CLASS_PREFIX = "jsamp-class:";

    /**
     * No-arg constructor prevents instantiation.
     */
    private DefaultClientProfile() {
    }

    /**
     * Returns a ClientProfile instance suitable for general purpose use.
     * By default this is currently the Standard Profile 
     * ({@link org.astrogrid.samp.xmlrpc.StandardClientProfile#getInstance
     *                                   StandardClientProfile.getInstance()}),
     * but the instance may be modified programatically or by use of
     * the SAMP_HUB environment variable.
     *
     * <p>If no instance has been set, the SAMP_HUB environment variable
     * is examined.  If it consists of the prefix "<code>jsamp-class:</code>" 
     * ({@link #HUBLOC_CLASS_PREFIX}) followed by the classname of a class
     * which implements {@link ClientProfile} and has a no-arg constructor,
     * then an instance of the named class is used.
     * Otherwise, an instance of {@link StandardClientProfile} is returned.
     *
     * <p>The instance is obtained lazily.
     *
     * @return   client profile instance
     */
    public static ClientProfile getProfile() {
        if ( profile_ == null ) {
            final ClientProfile profile;
            String hubloc = Platform.getPlatform().getEnv( HUBLOC_ENV );
            if ( hubloc == null || hubloc.trim().length() == 0 ) {
                profile = StandardClientProfile.getInstance();
            }
            else if ( hubloc.startsWith( HUBLOC_CLASS_PREFIX ) ) {
                String cname = hubloc.substring( HUBLOC_CLASS_PREFIX.length() );
                final Class clazz;
                try {
                    clazz = Class.forName( cname );
                }
                catch ( ClassNotFoundException e ) {
                    throw new IllegalArgumentException( "No profile class "
                                                      + cname, e );
                }
                try {
                    profile = (ClientProfile) clazz.newInstance();
                    logger_.info( "Using non-standard hub location: "
                                + HUBLOC_ENV + "=" + hubloc );
                }
                catch ( Throwable e ) {
                    throw (RuntimeException)
                          new RuntimeException( "Error instantiating custom "
                                              + "profile " + clazz.getName() )
                         .initCause( e );
                }
            }
            else if ( hubloc.startsWith( StandardClientProfile
                                        .STDPROFILE_HUB_PREFIX ) ) {
                profile = StandardClientProfile.getInstance();
            }
            else if ( hubloc.startsWith( WebClientProfile
                                        .WEBPROFILE_HUB_PREFIX ) ) {
                profile = WebClientProfile.getInstance();
            }
            else {
                throw new RuntimeException( "Can't make sense of " + HUBLOC_ENV
                                          + "=" + hubloc ); 
            }
            profile_ = profile;
        }
        return profile_;
    }

    /**
     * Sets the profile object which will be returned by {@link #getProfile}.
     *
     * @param  profile  default profile instance
     */
    public static void setProfile( ClientProfile profile ) {
        profile_ = profile;
    }
}
