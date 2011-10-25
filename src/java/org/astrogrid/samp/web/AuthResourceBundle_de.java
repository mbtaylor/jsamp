package org.astrogrid.samp.web;

/**
 * AuthResourceBundle with German text.
 *
 * @author   Markus Demleitner
 * @author   Mark Taylor
 * @since    1 Aug 2011
 */
public class AuthResourceBundle_de extends AuthResourceBundle {

    /**
     * Constructor.
     */
    public AuthResourceBundle_de() {
        super( new GermanContent() );
    }

    /**
     * Content implementation for English.
     */
    private static class GermanContent implements Content {
        public String windowTitle() {
            return "SAMP Zugriffskontrolle";
        }
        public String appIntroductionLines() {
            return "Folgendes Programm (vermutlich im Browser laufend)\n"
                 + "m\u00f6chte sich am SAMP Hub anmelden:";
        }
        public String nameWord() {
            return "Name";
        }
        public String originWord() {
            return "Auf Seite";
        }
        public String undeclaredWord() {
            return "Nicht gegeben";
        }
        public String privilegeWarningLines() {
            return "Wenn Sie dies zulassen, k\u00f6nnte der Dienst unter\n"
                 + "Umst\u00e4nden auf Dateien oder andere Resourcen auf\n"
                 + "Ihrem Rechner zugreifen k\u00f6nnen.";
        }
        public String adviceLines() {
            return "Lassen Sie die Verbindung nur zu, wenn Sie gerade\n"
                 + "auf einer Seite, der Sie vertrauen, eine Handlung\n"
                 + "ausgef\u00fchrt haben, die SAMP anspricht.";
        }
        public String questionLine() {
            return "Die Verbindung erlauben?";
        }
        public String yesWord() {
            return "Ja";
        }
        public String noWord() {
            return "Nein";
        }
    }
}
