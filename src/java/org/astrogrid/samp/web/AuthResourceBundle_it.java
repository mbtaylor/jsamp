package org.astrogrid.samp.web;

/**
 * AuthResourceBundle with English text.
 *
 * @author   Luigi Paioro
 * @author   Mark Taylor
 * @since    15 Jul 2011
 */
public class AuthResourceBundle_it extends AuthResourceBundle {

    /**
     * Constructor.
     */
    public AuthResourceBundle_it() {
        super( new ItalianContent() );
    }

    /**
     * Content implementation for Italian.
     */
    public static class ItalianContent implements Content {
        public String windowTitle() {
            return "Sicurezza del SAMP Hub";
        }
        public String appIntroductionLines() {
            return "Il seguente programma, probabilmente eseguito all'interno\n"
                 + "di un browser, chiede di essere registrato al SAMP Hub:";
        }
        public String nameWord() {
            return "Nome";
        }
        public String originWord() {
            return "Origine";
        }
        public String undeclaredWord() {
            return "non dichiarato";
        }
        public String privilegeWarningFormatLines() {
            return "Se ne consentite la registrazione, "
                 + "esso avr\u00e0 i privilegi\n"
                 + "di accesso dell''utente {0}, come, ad esempio,\n"
                 + "leggere e scrivere files.";
        }
        public String adviceLines() {
            return "Il vostro consenso dovrebbe essere dato solo se avete\n"
                 + "appena eseguito qualche azione con il browser,\n"
                 + "su un sito Web conosciuto, che vi aspettate possa aver\n"
                 + "causato questa richiesta.";
        }
        public String questionLine() {
            return "Autorizzate la registrazione?";
        }
        public String yesWord() {
            return "S\u00ec";
        }
        public String noWord() {
            return "No";
        }
    }
}
