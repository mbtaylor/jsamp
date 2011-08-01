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
    public static class GermanContent implements Content {
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
        public String privilegeWarningFormatLines() {
            return "Wenn Sie dies erlauben, hat das Programm im\n"
                 + "Wesentlichen alle Rechte des Benutzers {0}.\n"
                 + "Es kann insbesondere Dateien lesen und schreiben.";
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
