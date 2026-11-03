package sn.aar.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moteur de detection d'arnaques SMS, sans reseau : des regles ponderees en francais et en wolof,
 * adaptees aux arnaques courantes au Senegal (faux transferts Wave / Orange Money, demande de code,
 * faux agents, liens pieges, codes USSD, faux gains, faux proches, marabouts...).
 *
 * Java pur : aucune dependance Android, pour etre teste sur ordinateur.
 */
public final class Detecteur {

    public static final int EXP_INCONNU = 0;   // message colle a la main, expediteur inconnu
    public static final int EXP_NUMERO = 1;    // numero de telephone ordinaire
    public static final int EXP_COURT = 2;     // numero court (3 a 6 chiffres)
    public static final int EXP_NOM = 3;       // nom d'expediteur (Wave, OrangeMoney...)

    private Detecteur() {}

    // ---------------------------------------------------------------- conseils

    static final String C_CODE = "Ne donne jamais un code reçu par SMS, même à quelqu'un qui dit travailler chez Wave, Orange ou ta banque.";
    static final String C_ERREUR = "Ne renvoie jamais toi-même un « transfert par erreur » : vérifie d'abord ton solde. Si l'argent est vraiment arrivé, c'est à l'opérateur de corriger l'erreur.";
    static final String C_OFFICIEL = "Les messages officiels arrivent en général sous le nom du service, pas depuis un numéro ordinaire. Vérifie directement dans l'appli Wave ou avec #144# pour Orange Money.";
    static final String C_AGENT = "Un vrai agent ne te demande jamais ton code ni de l'argent par SMS ou par appel. Raccroche et appelle toi-même le service client officiel.";
    static final String C_GAIN = "On ne gagne pas à un jeu auquel on n'a pas joué. Ne paie jamais de frais pour recevoir un gain.";
    static final String C_FRAIS = "Un vrai gain, prêt ou colis ne se débloque pas en payant d'abord des « frais » par Wave ou Orange Money.";
    static final String C_PRESSION = "La pression (« urgent », « compte bloqué ») est une technique d'arnaque : prends le temps de vérifier avant d'agir.";
    static final String C_LIEN = "N'ouvre pas le lien. Pour vérifier, passe par l'appli officielle ou tape toi-même l'adresse du site.";
    static final String C_USSD = "Ne compose jamais un code *…# ou #…# envoyé par un inconnu : il peut transférer ton argent.";
    static final String C_PROCHE = "Appelle la personne sur son numéro habituel avant d'envoyer quoi que ce soit.";
    static final String C_ARGENT = "N'envoie pas d'argent à un numéro inconnu sans avoir vérifié de vive voix.";
    static final String C_PRET = "Méfie-toi des prêts, placements ou emplois qui promettent beaucoup et demandent de payer d'abord.";
    static final String C_MARABOUT = "Personne ne multiplie l'argent : ceux qui le promettent font payer d'abord, puis disparaissent.";
    static final String C_INFOS = "Ne donne jamais ta pièce d'identité, ton code secret ou tes informations bancaires à quelqu'un qui te contacte par SMS.";

    // ---------------------------------------------------------------- motifs (texte normalise)

    private static Pattern p(String re) {
        return Pattern.compile(re);
    }

    private static final String MONTANT_RE =
            "(?:\\d[\\d .]*\\d|\\d)\\s?(?:f|fcfa|cfa|frs?|francs?|xof|mille|k)\\b|\\b\\d{1,3}[ .]000\\b|\\b\\d{4,7}\\b";
    static final Pattern MONTANT = p(MONTANT_RE);
    static final Pattern ARGENT_MOTS = p("\\b(argent|xaalis|xalis|khaliss?|transfert|depot|wave|orange ?money|free ?money|om|credit|solde|fcfa|cfa|francs?|mille)\\b");

    static final Pattern PROTECTION = p(
            "\\bne\\s+(?:(?:le|la|les|lui|leur)\\s+|l')?(?:jamais\\s+)?(?:partag|communiqu|donn|divulgu|transmet|revel|envoi|envoy|dites|dis)\\w*"
            + "|\\bn'(?:envoy|envoi)\\w*"
            + "|\\b(?:jamais|a personne|avec personne|a quiconque)\\b");

    private static final String PAS_UN_CODE_SECRET =
            "(?! ?(?:wifi|wi-fi|de la porte|du portail|postal|promo|source|couleur|vestimentaire|de la route|du wifi|qr|barre|du cours))";
    static final Pattern CODE_DEMANDE = p(
            "\\b(?:envoi[es]?|envoyez|envoyer|renvoi[es]?|renvoyez|donne[sz]?|donner|redonne[sz]?|communique[sz]?|communiquer|"
            + "transmet[st]?|transmettez|transmettre|partage[sz]?|partager|dicte[sz]?|dis[- ]moi|dites[- ]moi|lis[- ]moi|lisez[- ]moi|"
            + "indique[sz]?[- ]moi|fais[- ]moi parvenir|faites[- ]moi parvenir|yonnee?|yonneel|yonnel|may|jox|joxe|wax|waxal)\\b"
            + "[^.!?\\n]{0,40}\\bcode\\b" + PAS_UN_CODE_SECRET);
    static final Pattern CODE_RECU = p(
            "\\bcode\\b" + PAS_UN_CODE_SECRET + "[^.!?\\n]{0,30}\\b(?:recu|arrive|venez de recevoir|viens de recevoir|vas recevoir|allez recevoir|va recevoir)\\b"
            + "|\\bcode bi\\b[^.!?\\n]{0,20}\\b(?:nga jot|ngay jot|jot|nga am)\\b");
    static final Pattern CODE_SECRET = p("\\bcode (?:secret|pin|confidentiel)\\b|\\bmot de passe\\b|\\bcvv\\b|\\bcryptogramme\\b");

    static final Pattern ERREUR = p(
            "\\bpar (?:erreur|megarde|inadvertance|accident|meprise)\\b"
            + "|\\berreur (?:de |d')?(?:transfert|numero|depot|envoi|manipulation|saisie)\\b"
            + "|\\b(?:me suis|je suis) trompee? (?:de |d'|sur le )?(?:numero|num|destinataire|personne)\\b"
            + "|\\btrompee? de (?:numero|num)\\b|\\bmauvais (?:numero|num|destinataire)\\b"
            + "|\\b(?:njuumte|njumte|nioumte|niumte|njoumte|nioumtee|njuumtee|njoumtee)\\b|\\bjuum(?:e|ee)?\\b");
    static final Pattern RENVOI = p(
            "\\b(?:renvo[iy]\\w*|rembours\\w*|retourne[rsz]?|retournez|rends|rendez|rendre|restitu\\w*|delloo?\\w*|dellu\\w*|delou\\w*|deloul\\w*|dellol\\w*)\\b");

    private static final String MARQUE_TETE = "(?:wave|orange ?money|om|orange|yas|mixx|free ?money|senelec|woyofal|wizall|expresso|sonatel|whatsapp)";
    static final Pattern PERSONA = p(
            "\\b(?:cher|chere|chers|cheres) (?:client|cliente|clients|abonne|abonnee|abonnes|utilisateur|utilisatrice|usager|partenaire)\\b"
            + "|\\bvotre (?:compte|solde|portefeuille|carte|abonnement|ligne|puce|compteur|facture|dossier|transaction|transfert|retrait|depot|code)\\b"
            + "|\\bton (?:compte|solde|portefeuille|compteur|code)\\b"
            + "|\\b(?:vous|t') ?informe\\b|\\bvous rappel\\w*|\\bnouveau solde\\b|\\bsolde (?:actuel|disponible)\\b"
            + "|\\bref(?:erence)?\\s?[:.#]|\\bid (?:de )?transaction\\b|\\bservice (?:client|clientele|technique)\\b"
            + "|\\bde la part (?:de |d')|^\\W*" + MARQUE_TETE + "\\b|\\b" + MARQUE_TETE + "\\s*:");

    static final Pattern FAUSSE_NOTIF = p(
            "\\b(?:vous avez|tu as|avez|as)(?: bien)? (?:recu|ete credite)\\b"
            + "|\\b(?:transfert|depot|credit|paiement) (?:de |d'un montant de |recu de )?\\d"
            + "|\\breception (?:de |d'un )(?:transfert|depot)\\b|\\bnouveau solde\\b");

    static final Pattern AGENT = p(
            "\\b(?:agent|service client|service clientele|conseiller|conseillere|technicien|responsable|direction|equipe|support|"
            + "administrateur|gestionnaire|charge de clientele|superviseur|employe)\\s+(?:de |d'|du |chez |a |au )?"
            + "(?:wave|orange ?money|orange|om|yas|mixx|free ?money|sonatel|senelec|woyofal|wizall|expresso|la banque|votre banque|ta banque|cbao|ecobank|uba|whatsapp)\\b"
            + "|\\bje suis (?:de |d'|du |chez |a |au |un agent |une agent |l'agent |le service )?(?:wave|orange|yas|senelec|sonatel|la banque|service client)\\b"
            + "|\\bje (?:travaille|bosse) (?:a|chez|pour) (?:wave|orange|yas|senelec|sonatel)\\b"
            + "|\\bmaa ngi (?:ci|nekk ci) (?:wave|orange|yas|senelec|sonatel)\\b");
    static final Pattern NEGATION_AVANT = p("\\b(?:meme|jamais|aucun|aucune|ni|pas)\\b");

    static final Pattern GAIN = p(
            "\\b(?:vous avez|tu as|avez|as) (?:gagne|remporte)\\b"
            + "|\\b(?:a|avez|as|ont|est|etes|es) ete (?:selectionne|selectionnee|tire|tiree|choisi|choisie)s?\\b"
            + "|\\bheureux gagnants?\\b|\\btirage au sort\\b|\\btombola\\b|\\bloterie\\b|\\bjackpot\\b|\\bgros lot\\b|\\bgrand prix\\b");
    static final Pattern FELICITATIONS = p("\\bfelicitations?\\b");
    static final Pattern BONUS = p(
            "\\b(?:bonus|cadeau|prime|recompense|ristourne|cashback|bourse|aide|subvention|don)\\b[^.!?\\n]{0,25}(?:" + MONTANT_RE + ")");
    static final Pattern FRAIS = p(
            "\\bfrais (?:de |d')?(?:dossier|retrait|livraison|transfert|douane|dedouanement|inscription|activation|deblocage|traitement|"
            + "enregistrement|validation|reception|expedition|ouverture|assurance)\\b"
            + "|\\bpour (?:recevoir|debloquer|retirer|valider|activer|reclamer|toucher|encaisser) (?:votre|ton|le|la|vos|tes|les) "
            + "(?:gain|gains|lot|prix|argent|cadeau|pret|credit|colis|bonus|prime|transfert|compte|aide)\\b"
            + "|\\b(?:payer|payez|paye|regler|reglez|verser|versez|envoyer|envoyez|envoie) (?:d'abord|avant|au prealable|les frais|des frais)\\b");

    static final Pattern URGENCE = p(
            "\\b(?:urgent|urgente|urgence|immediatement|tout de suite|sans delai|au plus vite|des que possible)\\b"
            + "|\\bdernier (?:rappel|delai|avertissement)\\b|\\bderniere (?:chance|relance)\\b"
            + "|\\b(?:avant|d'ici) (?:ce soir|demain|minuit|\\d+ ?h)\\b|\\bdans les? (?:prochaines )?\\d+ ?(?:h|heures?|minutes?|mn|min)\\b|\\bexpire\\w*");
    static final Pattern MENACE = p(
            "\\b(?:sera|seront|va etre|vont etre|risque d'etre|est|a ete|sont|ont ete)\\s(?:definitivement\\s)?"
            + "(?:bloque|suspendu|desactive|ferme|supprime|coupe|resilie|perdu|gele|retenu|annule|restreint|limite)e?s?\\b"
            + "|\\b(?:blocage|suspension|desactivation|fermeture|coupure|resiliation|suppression) (?:de |du |d')?(?:votre |ton |la |le )?"
            + "(?:compte|ligne|puce|numero|compteur|carte|electricite|courant|eau|whatsapp)\\b"
            + "|\\b(?:reactiver|reactivez|reactive|debloquer|debloquez|regulariser|regularisez|mettre a jour votre compte|mettez a jour votre compte|"
            + "verifier votre compte|verifiez votre compte|confirmer votre identite|confirmez votre identite)\\b"
            + "|\\b(?:poursuites?|plainte|convocation|mandat d'arret|amende)\\b");

    static final Pattern PROCHE = p(
            "\\b(?:c'est|cest|c) moi\\b|\\bnouveau (?:numero|num|nimero)\\b|\\bnouvelle puce\\b"
            + "|\\bchange(?:r)? de (?:numero|num|puce|telephone)\\b|\\bmon (?:ancien|autre) (?:numero|telephone|tel|portable)\\b"
            + "|\\b(?:telephone|tel|portable|phone)\\s(?:est\\s|s'est\\s)?(?:gate|tombe|casse|vole|en panne|perdu|eteint)\\b"
            + "|\\bje suis (?:bloque|bloquee|coince|coincee|a l'hopital|en difficulte|en panne)\\b|\\bbu bees\\b");

    static final Pattern ARGENT_DEMANDE = p(
            "\\b(?:envoie|envoies|envoyez|envoyer|fais|faites|depose|deposez|deposer|transfere|transferez|transferer|prete|pretez|preter|"
            + "avance|avancez|paye|payez|payer|verse|versez|yonnee?|yonneel|yobbal|yobbalal|may|jox|joxe|bayil|bayilal)"
            + "(?:[- ]?(?:moi|nous|ma|ko|ma ko|leen|nu))?\\b[^.!?\\n]{0,35}"
            + "(?:" + MONTANT_RE + "|\\bargent\\b|\\bxaalis\\b|\\bxalis\\b|\\bcredit\\b|\\bdepot\\b|\\btransfert\\b"
            + "|\\bpar (?:wave|om|orange money|free money|yas|mixx)\\b|\\bsur (?:wave|om|orange money|ce numero|mon numero)\\b)");

    static final Pattern PROMESSE = p(
            "\\bdoubl\\w*\\s(?:votre|ton|son|vos|tes|l')\\s?(?:argent|mise|capital|investissement|somme|epargne)\\b"
            + "|\\b(?:rendement|benefice|gain|profit|interet)s?\\s(?:garanti|assure|quotidien|journalier|hebdomadaire)s?\\b"
            + "|\\b\\d+ ?% (?:par|chaque|tous les) (?:jours?|semaines?|mois)\\b"
            + "|\\b(?:revenus?|argent|gains?) (?:facile|rapide|garanti|passif)s?\\b"
            + "|\\bgagne[rz]?\\s(?:jusqu'a\\s)?\\d[\\d .]*\\s?(?:f|fcfa|cfa|frs?|francs?|xof|mille|k)?\\s?(?:par|chaque|tous les) (?:jour|semaine|heure)\\b");
    static final Pattern ARGENT_FACILE = p(
            "\\b(?:pret|credit|financement|microcredit)s?\\s(?:rapide|immediat|express|instantane|facile|garanti|"
            + "sans (?:garantie|justificatif|dossier|interet|caution|condition))\\b"
            + "|\\btravail (?:a domicile|en ligne|depuis (?:chez vous|la maison|ton telephone))\\b"
            + "|\\b(?:cryptomonnaie|crypto|bitcoin|usdt|trading|forex|investissement en ligne)\\b"
            + "|\\b(?:recrutement|recrutons|offre d'emploi|on embauche)\\b");

    static final Pattern MARABOUT_FORT = p(
            "\\bmultiplication (?:de |d'|des )?(?:billets|argent|l'argent)\\b|\\b(?:porte[- ]?monnaie|portefeuille|valise|coffre) (?:magique|mystique)\\b"
            + "|\\b(?:richesse|fortune) (?:rapide|instantanee|mystique)\\b|\\bbillets? noirs?\\b");
    static final Pattern MARABOUT = p(
            "\\b(?:grand )?marabout\\b|\\bvoyante?\\b|\\bmedium\\b|\\bguerisseur\\b|\\bretour (?:de |d')?l'etre aime\\b|\\bdesenvoutement\\b");
    static final Pattern TELEPHONE_DANS_TEXTE = p("(?:\\+?221[ .-]?)?\\b7[05678][ .-]?\\d{3}[ .-]?\\d{2}[ .-]?\\d{2}\\b");

    static final Pattern INFOS = p(
            "\\b(?:envoie|envoyez|donne|donnez|communique|communiquez|confirme|confirmez|transmet|transmettez|indique|indiquez|renseigne|"
            + "renseignez|remplis|remplissez|mettez a jour|mets a jour)\\b[^.!?\\n]{0,40}"
            + "\\b(?:numero (?:de )?(?:carte|cni|piece|compte)|carte d'identite|carte bancaire|piece d'identite|cni|date de naissance|"
            + "mot de passe|identifiants?|code confidentiel|code secret|code pin|pin|cvv|rib|iban|coordonnees bancaires|"
            + "informations (?:personnelles|bancaires))\\b");

    static final Pattern USSD = p("[*#]\\d{2,4}(?:[*#]\\d*)*#");
    static final Pattern COMPOSE = p(
            "\\b(?:compose[rsz]?|composez|tape[rsz]?|tapez|fais|faites|faire|entre[rz]?|entrez|appuie[sz]?|appuyez|lance[sz]?|lancez|"
            + "saisis|saisissez|digite|bind|binde|bindal)\\b");
    static final Pattern LONG_NUMERO = p("\\d{8,}");

    static final Pattern URL_SCHEMA = p("(?:https?://|www\\.)[^\\s<>\"']+");
    static final Pattern URL_NUE = p(
            "(?<![@\\w.-])[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*"
            + "\\.(?:com|net|org|info|biz|xyz|top|click|icu|online|site|live|buzz|link|shop|store|sn|ci|ml|bf|gn|cm|fr|me|io|co|cc|ly|gd|gl|"
            + "to|ws|app|vip|club|win|bid|loan|work|tk|ga|cf|cfd|gq|ru|cn|in|pro|tv|mobi|page|dev|gy|id|at|su|cyou|sbs|lol|rest|today|"
            + "support|help|services|money|bonus|gift)(?::\\d+)?(?:/[^\\s<>\"']*)?(?![\\w-])");
    static final Pattern IP = p("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

    static final String[] RACCOURCISSEURS = {
            "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "is.gd", "v.gd", "ow.ly", "rb.gy", "shorturl.at",
            "t.ly", "tiny.cc", "s.id", "rebrand.ly", "shorte.st", "adf.ly", "bl.ink", "qrco.de", "tinu.be", "lnk.bio", "linktr.ee"
    };
    static final String[] TLD_RISQUEES = {
            "xyz", "top", "click", "icu", "online", "site", "live", "buzz", "link", "shop", "vip", "club", "win", "bid", "loan", "work",
            "tk", "ml", "ga", "cf", "cfd", "gq", "cyou", "sbs", "lol", "rest", "support", "help", "services", "money", "bonus", "gift"
    };
    static final String[] DOMAINES_OFFICIELS = {
            "wave.com", "orange.sn", "orange.com", "sonatel.sn", "senelec.sn", "yas.sn", "free.sn", "wizall.com", "expresso.sn",
            "whatsapp.com", "whatsapp.net", "facebook.com", "google.com", "gouv.sn", "cbao.sn", "ecobank.com", "dhl.com"
    };
    /** Mots de marque reperes dans un nom de domaine, avec le nom affiche. */
    static final String[][] MARQUES_DOMAINE = {
            {"wave", "Wave"}, {"orangemoney", "Orange Money"}, {"orange", "Orange"}, {"sonatel", "Orange"}, {"senelec", "Senelec"},
            {"woyofal", "Senelec"}, {"yas", "Yas"}, {"mixx", "Yas"}, {"freemoney", "Yas"}, {"free-money", "Yas"}, {"wizall", "Wizall"},
            {"expresso", "Expresso"}, {"whatsapp", "WhatsApp"}, {"ecobank", "Ecobank"}, {"cbao", "CBAO"}, {"dhl", "DHL"},
            {"gouv", "l'administration"}, {"impot", "l'administration"}
    };

    /** Marques citees dans le texte, dans l'ordre de priorite. */
    static final Object[][] MARQUES = {
            {p("\\bwave\\b"), "Wave"},
            {p("\\borange ?money\\b|\\bom\\b"), "Orange Money"},
            {p("\\bfree ?money\\b|\\bmixx\\b|\\byas\\b"), "Yas"},
            {p("\\bwizall\\b"), "Wizall"},
            {p("\\bexpresso\\b|\\be-?money\\b"), "Expresso"},
            {p("\\bsenelec\\b|\\bwoyofal\\b"), "Senelec"},
            {p("\\bsen ?'?eau\\b"), "Sen'Eau"},
            {p("\\borange\\b|\\bsonatel\\b"), "Orange"},
            {p("\\bcbao\\b|\\becobank\\b|\\buba\\b|\\bbicis\\b|\\bsgbs\\b|\\bsociete generale\\b|\\bbanque atlantique\\b|\\borabank\\b|\\bbnde\\b|\\bvotre banque\\b|\\bta banque\\b"), "ta banque"},
            {p("\\bwhatsapp\\b"), "WhatsApp"},
            {p("\\bdhl\\b|\\bla poste\\b"), "un service de livraison"},
            {p("\\bimpots?\\b|\\bdgid\\b|\\bministere\\b|\\bgouvernement\\b"), "l'administration"}
    };

    // ---------------------------------------------------------------- API

    /** Minuscules, sans accents, apostrophes et espaces unifies (les retours a la ligne sont gardes). */
    public static String normaliser(String s) {
        if (s == null) return "";
        String t = s.replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'').replace('`', '\'')
                .replace(' ', ' ').replace(' ', ' ');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.toLowerCase(Locale.ROOT).replace("œ", "oe").replace("æ", "ae");
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        return t;
    }

    public static int typeExpediteur(String exp) {
        if (exp == null) return EXP_INCONNU;
        String s = exp.trim();
        if (s.isEmpty()) return EXP_INCONNU;
        int chiffres = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return EXP_NOM;
            if (Character.isDigit(c)) chiffres++;
        }
        if (chiffres >= 3 && chiffres <= 6) return EXP_COURT;
        if (chiffres >= 7) return EXP_NUMERO;
        return EXP_INCONNU;
    }

    /** Indicatif d'un numero etranger (« +33 »...), ou null pour un numero senegalais ou inconnu. */
    public static String indicatifEtranger(String exp) {
        if (exp == null) return null;
        String s = exp.trim();
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) d.append(s.charAt(i));
        String n = d.toString();
        boolean international = s.startsWith("+") || n.startsWith("00");
        if (!international) return null;
        if (n.startsWith("00")) n = n.substring(2);
        if (n.startsWith("221") || n.length() < 8) return null;
        int len = n.startsWith("1") ? 1 : (n.length() > 11 ? 3 : 2);
        return "+" + n.substring(0, Math.min(len, n.length()));
    }

    public static Verdict analyser(String expediteur, String message) {
        String t = normaliser(message);
        int exp = typeExpediteur(expediteur);
        boolean officiel = exp == EXP_NOM || exp == EXP_COURT;
        List<Verdict.Signal> s = new ArrayList<Verdict.Signal>();
        if (t.trim().isEmpty()) return new Verdict(0, s);

        boolean argent = MONTANT.matcher(t).find() || ARGENT_MOTS.matcher(t).find();
        String marque = marque(t);
        String nomMarque = marque != null ? marque : "un opérateur";

        // 1. Demande de code
        boolean code = trouveNonProtege(CODE_DEMANDE, t) || trouveNonProtege(CODE_RECU, t);
        if (code) {
            s.add(sig("code", 45, "Te demande de donner un code reçu par SMS.", C_CODE));
        } else if (trouveNonProtege(CODE_SECRET, t)) {
            s.add(sig("secret", 20, "Parle de ton code secret ou de ton mot de passe.", C_CODE));
        }

        // 2. Informations personnelles
        if (trouveNonProtege(INFOS, t)) {
            s.add(sig("infos", 30, "Demande tes informations personnelles ou bancaires.", C_INFOS));
        }

        // 3. Faux transfert « par erreur »
        boolean erreur = argent && ERREUR.matcher(t).find();
        if (erreur && RENVOI.matcher(t).find()) {
            s.add(sig("erreur", 55, "Parle d'un transfert « par erreur » et demande de renvoyer l'argent : c'est l'arnaque la plus courante.", C_ERREUR));
        } else if (erreur) {
            s.add(sig("erreur", 30, "Parle d'un transfert d'argent envoyé « par erreur ».", C_ERREUR));
        }

        // 4. Se fait passer pour un operateur depuis un numero ordinaire
        boolean usurpe = false;
        if (marque != null && exp == EXP_NUMERO && PERSONA.matcher(t).find()) {
            usurpe = true;
            s.add(sig("usurpation", 40, "Se présente comme " + marque + ", mais vient d'un numéro ordinaire.", C_OFFICIEL));
        }

        // 5. Faux avis de transfert
        if (exp == EXP_NUMERO && argent && FAUSSE_NOTIF.matcher(t).find()) {
            s.add(sig("notif", 30, "Ressemble à un avis de transfert reçu, mais envoyé par un particulier.", C_OFFICIEL));
        }

        // 6. Faux agent
        if (!officiel && trouveSansNegationAvant(AGENT, t)) {
            usurpe = true;
            s.add(sig("agent", exp == EXP_NUMERO ? 40 : 30, "Quelqu'un prétend être un agent de " + nomMarque + ".", C_AGENT));
        }

        // 7. Faux gain, bonus, frais
        boolean gain = GAIN.matcher(t).find();
        if (gain) {
            s.add(sig("gain", officiel ? 15 : 30, "Annonce un gain ou un tirage au sort.", C_GAIN));
        } else if (argent && FELICITATIONS.matcher(t).find()) {
            s.add(sig("felicitations", 10, "Commence par des « félicitations » et parle d'argent.", C_GAIN));
        }
        if (BONUS.matcher(t).find()) {
            s.add(sig("bonus", 15, "Promet un bonus, un cadeau ou une aide en argent.", C_GAIN));
        }
        boolean frais = FRAIS.matcher(t).find();
        if (frais) {
            s.add(sig("frais", 20, "Demande de payer des frais pour recevoir quelque chose.", C_FRAIS));
        }

        // 8. Pression et menaces
        if (URGENCE.matcher(t).find()) {
            s.add(sig("urgence", officiel ? 5 : 15, "Te met la pression (« urgent », délai très court).", C_PRESSION));
        }
        if (MENACE.matcher(t).find()) {
            s.add(sig("menace", officiel ? 10 : 20, "Menace de bloquer ton compte ou de couper un service.", C_PRESSION));
        }

        // 9. Liens
        boolean lienSuspect = analyserLiens(t, exp, s);

        // 10. Codes USSD
        boolean ussd = analyserUssd(t, exp, s);

        // 11. Faux proche
        boolean proche = !officiel && PROCHE.matcher(t).find();
        if (proche) {
            s.add(sig("proche", 20, "Se présente comme un proche qui a changé de numéro ou a un problème.", C_PROCHE));
        }

        // 12. Demande d'argent
        boolean demandeArgent = !officiel && ARGENT_DEMANDE.matcher(t).find();
        if (demandeArgent) {
            s.add(sig("argent", 15, "Te demande d'envoyer de l'argent.", C_ARGENT));
        }
        if (proche && demandeArgent) {
            s.add(sig("proche_argent", 15, "Un « proche » au nouveau numéro qui réclame de l'argent : schéma d'arnaque connu.", C_PROCHE));
        }

        // 13. Argent facile, marabouts
        if (PROMESSE.matcher(t).find()) {
            s.add(sig("promesse", 35, "Promet de gagner beaucoup d'argent, vite et sans risque.", C_PRET));
        }
        boolean pret = ARGENT_FACILE.matcher(t).find();
        if (pret) {
            s.add(sig("pret", 20, "Propose un prêt facile, un emploi en ligne ou un placement.", C_PRET));
        }
        if (MARABOUT_FORT.matcher(t).find()) {
            s.add(sig("marabout", 50, "Promet de multiplier l'argent ou une richesse « magique ».", C_MARABOUT));
        } else if (!officiel && MARABOUT.matcher(t).find() && (argent || TELEPHONE_DANS_TEXTE.matcher(t).find())) {
            s.add(sig("marabout", 25, "Publicité de voyant ou de « marabout » avec un numéro ou de l'argent.", C_MARABOUT));
        }

        // 14. Combinaisons aggravantes
        if (frais && (gain || pret || contient(s, "bonus"))) {
            s.add(sig("payer_avant", 15, "Il faudrait payer d'abord pour recevoir de l'argent : le schéma classique de l'arnaque.", C_FRAIS));
        }
        if (lienSuspect && (frais || code || contient(s, "infos") || contient(s, "menace"))) {
            s.add(sig("lien_plus", 10, "Un lien douteux combiné à une demande de paiement ou d'informations.", C_LIEN));
        }
        if (usurpe && (code || lienSuspect || ussd)) {
            s.add(sig("usurpation_plus", 10, "Usurpe " + nomMarque + " et réclame une action risquée (code, lien ou code USSD).", C_AGENT));
        }

        // 15. Avertissement de securite officiel (rassurant)
        if (exp != EXP_NUMERO && !code && PROTECTION.matcher(t).find()) {
            s.add(sig("protege", -25, "Contient un avertissement de sécurité (« ne le partagez pas »), comme les vrais messages officiels.", null));
        }

        int total = 0;
        for (Verdict.Signal x : s) total += x.poids;

        // 16. Numero etranger
        String etranger = exp == EXP_NUMERO ? indicatifEtranger(expediteur) : null;
        if (etranger != null && total >= 15) {
            s.add(sig("etranger", 10, "Vient d'un numéro étranger (" + etranger + ").", null));
            total += 10;
        }

        Collections.sort(s, new Comparator<Verdict.Signal>() {
            @Override
            public int compare(Verdict.Signal a, Verdict.Signal b) {
                return b.poids - a.poids;
            }
        });
        return new Verdict(total, s);
    }

    // ---------------------------------------------------------------- details

    private static Verdict.Signal sig(String id, int poids, String raison, String conseil) {
        return new Verdict.Signal(id, poids, raison, conseil);
    }

    private static boolean contient(List<Verdict.Signal> s, String id) {
        for (Verdict.Signal x : s) if (x.id.equals(id)) return true;
        return false;
    }

    static String marque(String t) {
        for (Object[] m : MARQUES) {
            if (((Pattern) m[0]).matcher(t).find()) return (String) m[1];
        }
        return null;
    }

    /** Vrai si le motif apparait hors d'une phrase de mise en garde (« ne le partagez avec personne »). */
    static boolean trouveNonProtege(Pattern motif, String t) {
        Matcher m = motif.matcher(t);
        while (m.find()) {
            String fenetre = t.substring(Math.max(0, m.start() - 60), Math.min(t.length(), m.end() + 60));
            if (!PROTECTION.matcher(fenetre).find()) return true;
        }
        return false;
    }

    /** Vrai si le motif apparait sans « meme », « jamais »... juste avant (« meme un agent Wave »). */
    static boolean trouveSansNegationAvant(Pattern motif, String t) {
        Matcher m = motif.matcher(t);
        while (m.find()) {
            String avant = t.substring(Math.max(0, m.start() - 20), m.start());
            if (!NEGATION_AVANT.matcher(avant).find()) return true;
        }
        return false;
    }

    /** Domaines cites dans le texte, sans doublon. */
    public static List<String> domaines(String t) {
        Set<String> out = new LinkedHashSet<String>();
        Matcher m = URL_SCHEMA.matcher(t);
        while (m.find()) out.add(domaine(m.group()));
        Matcher n = URL_NUE.matcher(t);
        while (n.find()) out.add(domaine(n.group()));
        out.remove("");
        return new ArrayList<String>(out);
    }

    static String domaine(String url) {
        String u = url;
        int i = u.indexOf("://");
        if (i >= 0) u = u.substring(i + 3);
        int at = u.lastIndexOf('@', indexFinHote(u));
        if (at >= 0) u = u.substring(at + 1);
        u = u.substring(0, indexFinHote(u));
        while (u.endsWith(".") || u.endsWith(",")) u = u.substring(0, u.length() - 1);
        if (u.startsWith("www.")) u = u.substring(4);
        return u;
    }

    private static int indexFinHote(String u) {
        int fin = u.length();
        for (char c : new char[]{'/', ':', '?', '#', ')', ']', '"', '\''}) {
            int k = u.indexOf(c);
            if (k >= 0 && k < fin) fin = k;
        }
        return fin;
    }

    private static boolean finitPar(String d, String officiel) {
        return d.equals(officiel) || d.endsWith("." + officiel);
    }

    static boolean estOfficiel(String d) {
        for (String o : DOMAINES_OFFICIELS) if (finitPar(d, o)) return true;
        return false;
    }

    static boolean analyserLiens(String t, int exp, List<Verdict.Signal> s) {
        List<String> doms = domaines(t);
        if (doms.isEmpty()) return false;
        boolean suspect = false, nonOfficiel = false, raccourci = false, ip = false, risque = false, whatsapp = false;
        String imite = null, domImite = null;
        for (String d : doms) {
            if (d.equals("wa.me") || d.equals("chat.whatsapp.com")) {
                whatsapp = true;
                continue;
            }
            if (estOfficiel(d)) continue;
            nonOfficiel = true;
            if (IP.matcher(d).matches()) ip = true;
            for (String r : RACCOURCISSEURS) if (finitPar(d, r)) raccourci = true;
            int point = d.lastIndexOf('.');
            String tld = point >= 0 ? d.substring(point + 1) : "";
            for (String r : TLD_RISQUEES) if (r.equals(tld)) risque = true;
            String sansTld = point >= 0 ? d.substring(0, point) : d;
            if (imite == null) {
                for (String[] m : MARQUES_DOMAINE) {
                    if (sansTld.contains(m[0])) {
                        imite = m[1];
                        domImite = d;
                        break;
                    }
                }
            }
        }
        if (imite != null) {
            s.add(sig("lien_imite", 35, "Lien qui imite " + imite + " (" + domImite + ").", C_LIEN));
            suspect = true;
        }
        if (ip) {
            s.add(sig("lien_ip", 30, "Lien vers une adresse IP au lieu d'un vrai site.", C_LIEN));
            suspect = true;
        }
        if (raccourci) {
            s.add(sig("lien_court", 25, "Lien raccourci : impossible de voir où il mène.", C_LIEN));
            suspect = true;
        }
        if (risque) {
            s.add(sig("lien_tld", 10, "Lien vers un site à l'extension peu fiable.", C_LIEN));
            suspect = true;
        }
        if (whatsapp && exp != EXP_NOM) {
            s.add(sig("lien_whatsapp", 10, "T'invite à écrire à un inconnu sur WhatsApp.", C_LIEN));
            suspect = true;
        }
        if (nonOfficiel && exp == EXP_NUMERO) {
            s.add(sig("lien_numero", 10, "Contient un lien envoyé par un numéro ordinaire.", C_LIEN));
            suspect = true;
        }
        return suspect;
    }

    static boolean analyserUssd(String t, int exp, List<Verdict.Signal> s) {
        Matcher m = USSD.matcher(t);
        boolean imperatif = false, numero = false;
        while (m.find()) {
            String avant = t.substring(Math.max(0, m.start() - 40), m.start());
            if (COMPOSE.matcher(avant).find()) imperatif = true;
            if (LONG_NUMERO.matcher(m.group()).find()) numero = true;
        }
        if (!imperatif && !numero) return false;
        if (exp == EXP_NOM && !numero) return false;
        int poids = 25 + (numero ? 20 : 0) + (exp == EXP_NUMERO ? 10 : 0);
        String raison = numero
                ? "Te demande de composer un code qui contient un numéro : il peut envoyer ton argent à quelqu'un."
                : "Te demande de composer un code USSD (*…# ou #…#).";
        s.add(sig("ussd", poids, raison, C_USSD));
        return true;
    }
}
