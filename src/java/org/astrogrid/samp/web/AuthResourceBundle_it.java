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
    private static class ItalianContent implements Content {
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
        public String privilegeWarningLines() {
            return "Se ne consentite la registrazione, esso potrebbe accedere\n"
                 + "ai files locali e ad altre risorse del vostro computer.";
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
