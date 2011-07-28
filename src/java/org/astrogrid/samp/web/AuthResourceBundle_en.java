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
        public String windowTitle() {
            return "SAMP Hub Security";
        }
        public String appIntroductionLines() {
            return "The following application, probably running in a browser,\n"
                 + "is requesting SAMP Hub registration:";
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
        public String privilegeWarningFormatLines() {
            return "If you permit this, it will have most of the privileges\n"
                 + "of user {0}, such as file read/write.";
        }
        public String adviceLines() {
            return "You should only accept if you have just performed\n"
                 + "some action in the browser, on a web site you trust,\n"
                 + "that you expect to have caused this.";
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
