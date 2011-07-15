package org.astrogrid.samp.web;

import java.util.Enumeration;
import java.util.Hashtable;
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

    public static final String APP_INTRODUCTION_LINES;
    public static final String NAME_WORD;
    public static final String ORIGIN_WORD;
    public static final String UNDECLARED_WORD;
    public static final String PRIVILEGE_WARNING_FORMAT_LINES;
    public static final String ADVICE_LINES;
    public static final String QUESTION_LINE;
    public static final String YES_WORD;
    public static final String NO_WORD;
    private static final String[] keys_ = new String[] {
        APP_INTRODUCTION_LINES = "appIntroductionLines",
        NAME_WORD = "nameWord",
        ORIGIN_WORD = "originWord",
        UNDECLARED_WORD = "undeclaredWord",
        PRIVILEGE_WARNING_FORMAT_LINES = "privilegeWarningFormatLines",
        ADVICE_LINES = "adviceLines",
        QUESTION_LINE = "questionLine",
        YES_WORD = "yesWord",
        NO_WORD = "noWord",
    };
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
        map_.put( APP_INTRODUCTION_LINES, content.appIntroductionLines() );
        map_.put( NAME_WORD, content.nameWord() );
        map_.put( ORIGIN_WORD, content.originWord() );
        map_.put( UNDECLARED_WORD, content.undeclaredWord() );
        map_.put( PRIVILEGE_WARNING_FORMAT_LINES,
                  content.privilegeWarningFormatLines() );
        map_.put( ADVICE_LINES, content.adviceLines() );
        map_.put( QUESTION_LINE, content.questionLine() );
        map_.put( YES_WORD, content.yesWord() );
        map_.put( NO_WORD, content.noWord() );
        assert hasAllKeys( this );
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
        if ( hasAllKeys( bundle ) ) {
            return new Content() {
                public String[] appIntroductionLines() {
                    return bundle.getStringArray( APP_INTRODUCTION_LINES );
                }
                public String nameWord() {
                    return bundle.getString( NAME_WORD );
                }
                public String originWord() {
                    return bundle.getString( ORIGIN_WORD );
                }
                public String undeclaredWord() {
                    return bundle.getString( UNDECLARED_WORD );
                }
                public String[] privilegeWarningFormatLines() {
                    return bundle
                          .getStringArray( PRIVILEGE_WARNING_FORMAT_LINES );
                }
                public String[] adviceLines() {
                    return bundle.getStringArray( ADVICE_LINES );
                }
                public String questionLine() {
                    return bundle.getString( QUESTION_LINE );
                }
                public String yesWord() {
                    return bundle.getString( YES_WORD );
                }
                public String noWord() {
                    return bundle.getString( NO_WORD );
                }
            };
        }
        else {
            logger_.warning( "Some keys missing from localised "
                           + "auth resource bundle; use English" );
            return getDefaultContent();
        }
    }

    /**
     * Determines if a bundle has all the required keys for this class.
     *
     * @param  bundle  bundle to test
     * @return   true iff bundle has all required keys
     */
    private static boolean hasAllKeys( ResourceBundle bundle ) {
        for ( int ik = 0; ik < keys_.length; ik++ ) {
            if ( bundle.getObject( keys_[ ik ] ) == null ) {
                return false;
            }
        }
        return true;
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
