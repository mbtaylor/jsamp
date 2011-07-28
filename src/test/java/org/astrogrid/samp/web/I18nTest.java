package org.astrogrid.samp.web;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import junit.framework.TestCase;

public class I18nTest extends TestCase {

    private static final Locale[] locales_ = new Locale[] {
        Locale.ENGLISH,
        Locale.FRENCH,
    };
    private static final String[] suffixes_ = new String[] {
        "_en", "_fr",
    };
    private static final Method[] contentMethods_ =
        AuthResourceBundle.getContentMethods();

    public void testTools() throws Exception {
        assertEquals( toMap( getContent( Locale.ENGLISH ) ),
                      toMap( getContent( Locale.UK ) ) );
    }

    public void testAuthResourceBundle() throws Exception {
        Set mapSet = new HashSet();
        for ( int il = 0; il < locales_.length; il++ ) {
            Locale locale = locales_[ il ];
            ResourceBundle bundle =
                ResourceBundle.getBundle( AuthResourceBundle.class.getName(),
                                          locale );
            AuthResourceBundle.checkHasAllKeys( bundle );
            AuthResourceBundle.Content content =
                AuthResourceBundle.getAuthContent( bundle );
            Map map = toMap( content );
            for ( Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                String value = (String) entry.getValue();
                assertNotNull( value );
                assertTrue( value.length() > 0 );
            }
            assertTrue( "Reuse content for " + locale,
                        ! mapSet.contains( map ) );
            mapSet.add( map );
        }
    }

    /**
     * This one does something different - by picking the resources by 
     * locale-specific name, it avoids getting a parent chain.
     * In this way it can check if any resources are missing
     * (if you do it the more obvious way, missing ones are filled in by
     * the parent, which we don't want for testing purposes).
     */
    public void testAuthSuffixes() {
        for ( int is = 0; is < suffixes_.length; is++ ) {
            ResourceBundle bundle =
                ResourceBundle.getBundle( AuthResourceBundle.class.getName()
                                        + suffixes_[ is ],
                                          Locale.US );
            AuthResourceBundle.checkHasAllKeys( bundle );
        }
    }

    private static AuthResourceBundle.Content getContent( Locale locale ) {
        ResourceBundle bundle =
            ResourceBundle.getBundle( AuthResourceBundle.class.getName(),
                                      locale );
        return AuthResourceBundle.getAuthContent( bundle );
    }

    private static Map toMap( AuthResourceBundle.Content content )
            throws Exception {
        Map map = new HashMap();
        for ( int im = 0; im < contentMethods_.length; im++ ) {
            Method method = contentMethods_[ im ];
            map.put( method.getName(),
                     (String) method.invoke( content, new Object[ 0 ] ) );
        }
        return map;
    }
}
