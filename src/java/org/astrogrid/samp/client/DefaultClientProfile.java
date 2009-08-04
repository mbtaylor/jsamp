package org.astrogrid.samp.client;

import org.astrogrid.samp.xmlrpc.StandardClientProfile;

/**
 * Factory which supplies the default ClientProfile for use by SAMP clients.
 * By using this class to obtain ClientProfile instances, applications 
 * can be used with non-standard profiles supplied at runtime without
 * requiring any code changes.
 *
 * @author   Mark Taylor
 * @since    4 Aug 2009
 */
public class DefaultClientProfile {

    /**
     * Name of the property which determines the default client profile
     * instance ({@value}).
     * If it is undefined or has the value "<code>standard</code>"
     * a standard profile is used, otherwise the value must be the
     * full classname of a class which implements {@link ClientProfile}
     * and has a no-arg constructor.
     */
    public static final String PROFILE_PROP = "jsamp.profile";

    private static ClientProfile profile_;

    /**
     * Returns a ClientProfile instance suitable for general purpose use.
     * By default this is currently the Standard Profile 
     * ({@link org.astrogrid.samp.xmlrpc.StandardClientProfile#getInstance
     *                                   StandardClientProfile.getInstance()}),
     * but the instance may be modified programatically or using system 
     * properties.  The instance is obtained lazily.
     *
     * @return   client profile instance
     */
    public static ClientProfile getProfile() {
        if ( profile_ == null ) {
            String profName = System.getProperty( PROFILE_PROP, "standard" );
            if ( "standard".equals( profName ) ||
                 profName.trim().length() == 0 ) {
                profile_ = StandardClientProfile.getInstance();
            }
            else {
                Class clazz;
                try {
                    clazz = Class.forName( profName );
                }
                catch ( ClassNotFoundException e ) {
                    String msg = new StringBuffer()
                        .append( PROFILE_PROP )
                        .append( " value " )
                        .append( '"' )
                        .append( profName )
                        .append( '"' )
                        .append( " neither known value nor classname" )
                        .toString();
                    throw new IllegalArgumentException( msg );
                }
                try {
                    profile_ = (ClientProfile) clazz.newInstance();
                }
                catch ( Throwable e ) {
                    throw (RuntimeException)
                          new RuntimeException( "Error instantiating custom "
                                              + "profile "
                                              + clazz.getName() )
                         .initCause( e );
                }
            }
        }
        return profile_;
    }

    /**
     * Sets the profile object which will be returned by {@link #getProfile}.
     *
     * @param  profile  default profile instance
     */
    public void setProfile( ClientProfile profile ) {
        profile_ = profile;
    }
}
