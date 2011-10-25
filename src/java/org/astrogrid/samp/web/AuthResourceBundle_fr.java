package org.astrogrid.samp.web;

/**
 * AuthResourceBundle with French text.
 *
 * @author   Thomas Boch
 * @author   Mark Taylor
 * @since    23 Aug 2011
 */
public class AuthResourceBundle_fr extends AuthResourceBundle {

    /**
     * Constructor.
     */
    public AuthResourceBundle_fr() {
        super( new FrenchContent() );
    }

    /**
     * Content implementation for French.
     */
    private static class FrenchContent implements Content {
        public String windowTitle() {
            return "Avertissement de s\u00e9curit\u00e9 du hub SAMP";
        }
        public String appIntroductionLines() {
            return "L'application suivante, qui s'ex\u00e9cute probablement "
                 + "depuis un\n"
                 + "navigateur, demande \u00e0 s'enregistrer "
                 + "aupr\u00e8s du hub SAMP:";
        }
        public String nameWord() {
            return "Nom";
        }
        public String originWord() {
            return "Origine";
        }
        public String undeclaredWord() {
            return "Inconnue";
        }
        public String privilegeWarningLines() {
            return "Si vous l'autorisez, elle pourra acc\u00e9der aux "
                 + "fichiers locaux\n"
                 + "et autres ressources de votre ordinateur.";
        }
        public String adviceLines() {
            return "Acceptez uniquement si vous venez d'effectuer dans le "
                 + "navigateur\n"
                 + "une action, sur un site de confiance, susceptible d'avoir "
                 + "entra\u00een\u00e9\n"
                 + "cette demande.";
        }
        public String questionLine() {
            return "Acceptez-vous?";
        }
        public String yesWord() {
            return "Oui";
        }
        public String noWord() {
            return "Non";
        }
    }
}
