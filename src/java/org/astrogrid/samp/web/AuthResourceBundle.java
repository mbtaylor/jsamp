package org.astrogrid.samp.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * ResourceBundle for internationalising the Web Profile authorization
 * dialogue.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2011
 */
public class AuthResourceBundle extends ResourceBundle {

    private final Hashtable map_;
    private static final Logger logger_ =
        Logger.getLogger( AuthResourceBundle.class.getName() );

    public AuthResourceBundle() {
        this( getDefaultContent() );
    }

    /**
     * Constructs a bundle based on a Content implementation.
     *
     * @param  content  contains information required for bundle
     */
    protected AuthResourceBundle( Content content ) {
        map_ = new Hashtable();
        Method[] methods = Content.class.getMethods();
        Object[] noArgs = new Object[ 0 ];
        for ( int im = 0; im < methods.length; im++ ) {
            Method method = methods[ im ];
            String mname = method.getName();
            try {
                map_.put( method.getName(), method.invoke( content, noArgs ) );
            }
            catch ( IllegalAccessException e ) {
                throw (RuntimeException)
                      new RuntimeException( "Failed to call method "
                                          + method.getName() )
                    .initCause( e );
            }
            catch ( InvocationTargetException e ) {
                throw (RuntimeException)
                      new RuntimeException( "Failed to call method "
                                          + method.getName() )
                    .initCause( e );
            }
        }
        checkHasAllKeys( this );
    }

    protected final Object handleGetObject( String key ) {
        return map_.get( key );
    }

    public final Enumeration getKeys() {
        return map_.keys();
    }

    /**
     * Returns a Content object based on a bundle which has the keys
     * that AuthResourceBundle is supposed to have.
     * If any of the required keys are missing, the result falls back
     * to a default bundle.
     *
     * @param  bundle  resource bundle
     * @return  content object guaranteed to have non-null contents for
     *          all its attributes
     */
    public static Content getAuthContent( final ResourceBundle bundle ) {
        try {
            checkHasAllKeys( bundle );
        }
        catch ( MissingResourceException e ) {
            logger_.warning( "Some keys missing from localised auth resource "
                           + "bundle; using English" );
            return getDefaultContent();
        }
        InvocationHandler ihandler = new InvocationHandler() {
            public Object invoke( Object proxy, Method method,
                                  Object[] args ) throws Throwable {
                String key = method.getName();
                Class rclazz = method.getReturnType();
                if ( String.class.equals( rclazz ) ) {
                    return bundle.getString( key );
                }
                else if ( String[].class.equals( rclazz ) ) {
                    return bundle.getStringArray( key );
                }
                else {
                    throw new RuntimeException( "Unsuitable return type "
                                              + rclazz.getName()
                                              + " (shouldn't happen)" );
                }
            }
        };
        return (Content)
               Proxy
              .newProxyInstance( AuthResourceBundle.class.getClassLoader(),
                                 new Class[] { Content.class }, ihandler );
    }

    /**
     * Determines if a bundle has all the required keys for this class.
     *
     * @param  bundle  bundle to test
     * @return   true iff bundle has all required keys
     */
    private static void checkHasAllKeys( ResourceBundle bundle ) {
        Collection bkeys = new HashSet();
        for ( Enumeration en = bundle.getKeys(); en.hasMoreElements(); ) {
            bkeys.add( en.nextElement() );
        }
        Collection mnames = new HashSet();
        Method[] methods = Content.class.getMethods();
        for ( int im = 0; im < methods.length; im++ ) {
            mnames.add( methods[ im ].getName() );
        }
        mnames.removeAll( bkeys );
        if ( ! mnames.isEmpty() ) {
            throw new MissingResourceException(
                          "Missing resources " + mnames,
                          AuthResourceBundle.class.getName(),
                          mnames.iterator().next().toString() );
        }
    }

    /**
     * Returns a default Content implementation.
     *
     * @return  english content
     */
    private static Content getDefaultContent() {
        return new AuthResourceBundle_en.EnglishContent();
    }

    /**
     * Defines the keys and value types required for a bundle of this class.
     * See the English language implementation,
     * {link AuthResourceBundle_en.EnglishContent} for example text.
     *
     * <p>All methods should return one or more strings which will be
     * used as displayed screen lines, so they shouldn't be too long.
     *
     * <p>The method names define the keys which can be used if a
     * property resource file is used to supply the content.
     */
    public static interface Content {

        /**
         * Returns lines introducing the registration request.
         */
        String[] appIntroductionLines();

        /**
         * Returns the word meaning "Name" (initial capitalised).
         */
        String nameWord();

        /**
         * Returns the word meaning "Origin" (initial capitalised).
         */
        String originWord();

        /**
         * Returns the word meaning "undeclared" (not capitalised).
         */
        String undeclaredWord();

        /**
         * Returns lines suitable for use as a MessageFormat explaining
         * the privileges that a registered client will have.
         * The token "{0}" will be replaced with the name of the current user.
         */
        String[] privilegeWarningFormatLines();

        /**
         * Returns lines with advice on whether you should accept or decline.
         */
        String[] adviceLines();

        /**
         * Returns a line asking whether to authorize (yes/no).
         */
        String questionLine();

        /**
         * Returns the word meaning "Yes" (initial capitalised).
         */
        String yesWord();

        /**
         * Returns the word meaning "No" (initial capitalised).
         */
        String noWord();
    }
}
