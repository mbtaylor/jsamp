package org.astrogrid.samp.web;

/**
 * AuthResourceBundle with English text.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2011
 */
public class AuthResourceBundle_en extends AuthResourceBundle {

    /**
     * Constructor.
     */
    public AuthResourceBundle_en() {
        super( new EnglishContent() );
    }

    /**
     * Content implementation for English.
     */
    public static class EnglishContent implements Content {
        public String[] appIntroductionLines() {
            return new String[] {
                "The following application, probably running in a browser,",
                "is requesting SAMP Hub registration:",
            };
        }
        public String nameWord() {
            return "Name";
        }
        public String originWord() {
            return "Origin";
        }
        public String undeclaredWord() {
            return "undeclared";
        }
        public String[] privilegeWarningFormatLines() {
            return new String[] {
                "If you permit this, it will have most of the privileges",
                "of user {0}, such as file read/write.",
            };
        }
        public String[] adviceLines() {
            return new String[] {
                "You should only accept if you have just performed",
                "some action in the browser, on a web site you trust,",
                "that you expect to have caused this.",
            };
        }
        public String questionLine() {
            return "Do you authorize connection?";
        }
        public String yesWord() {
            return "Yes";
        }
        public String noWord() {
            return "No";
        }
    }
}
