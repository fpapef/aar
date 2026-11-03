import sn.aar.core.Detecteur;
import sn.aar.core.Verdict;

/**
 * Teste le moteur sur ordinateur, sans Android :
 *   javac -d /tmp/aar-test src/sn/aar/core/*.java desktop-test/TestDetecteur.java
 *   java -cp /tmp/aar-test TestDetecteur
 */
public class TestDetecteur {

    static int ok = 0, ko = 0;

    static void cas(int attendu, String exp, String texte) {
        Verdict v = Detecteur.analyser(exp, texte);
        boolean bon = v.niveau == attendu;
        if (bon) ok++; else ko++;
        System.out.println((bon ? "OK  " : "KO  ") + "[" + nom(v.niveau) + " " + v.score + "] attendu " + nom(attendu)
                + " | " + (exp == null ? "-" : exp) + " | " + texte.replace('\n', ' '));
        if (!bon || System.getenv("DETAIL") != null) {
            for (Verdict.Signal s : v.signaux) System.out.println("        " + s.poids + "  " + s.raison);
        }
    }

    static String nom(int n) {
        return n == Verdict.ARNAQUE ? "ARNAQUE" : n == Verdict.SUSPECT ? "SUSPECT" : "SUR";
    }

    public static void main(String[] a) {
        int A = Verdict.ARNAQUE, S = Verdict.SUSPECT, R = Verdict.SUR;

        System.out.println("== Arnaques");
        cas(A, "+221 77 845 12 30", "Bonjour, je vous ai envoyé 25 000 F par erreur sur votre numéro Wave. Merci de me les renvoyer svp c'est urgent.");
        cas(A, "781234567", "Orange Money: Vous avez reçu 50000F de 776543210. Nouveau solde: 52000F. Ref: CI230915.1234.A12345");
        cas(A, "776001122", "Salam, dama la yonnee 10000 ci njuumte, delloo ma ko su la nexee");
        cas(A, "+221701112233", "Cher client Wave, votre compte sera suspendu. Pour le réactiver, cliquez sur http://wave-sn-securite.com/verif");
        cas(A, "763334455", "FELICITATIONS! Vous avez gagné 500 000F à la tombola Orange. Pour recevoir votre lot, payez les frais de dossier de 5000F au 785556677");
        cas(A, "775556677", "Bonjour, je suis agent Wave. Pour vérifier votre compte, envoyez-moi le code que vous venez de recevoir.");
        cas(A, "779998877", "Composez #144*1*1*771234567*20000# pour activer votre bonus de 10000F");
        cas(A, "+221 76 123 45 67", "Maman c'est moi, j'ai changé de numéro, mon téléphone est tombé. Envoie-moi 20000 par Wave stp c'est urgent");
        cas(A, "+221771112222", "SENELEC: votre compteur sera coupé aujourd'hui. Payez 15000F au 774443322 par Wave pour éviter la coupure.");
        cas(A, "771234500", "Recrutement urgent : travail en ligne, gagnez 25000F par jour. Contactez-nous sur WhatsApp wa.me/221771234500");
        cas(A, "INFO", "Votre colis est bloqué à la douane. Payez les frais de livraison ici : https://bit.ly/3xYzAb");
        cas(A, "+221 70 000 11 22", "Votre numéro a été tiré au sort pour gagner un iPhone 15. Cliquez : http://promo-orange-sn.xyz");
        cas(A, "+2250707070707", "Multiplication de billets garantie, porte-monnaie magique. Appelez le grand marabout au +225 07 07 07 07 07");
        cas(A, "+221775550000", "Service client Orange Money : suite à une erreur de transfert, merci de me retourner les 15 000 FCFA reçus.");
        cas(A, "772223344", "Yas Mixx: vous avez reçu un bonus de 25000F. Pour le retirer donnez le code reçu par SMS à notre agent.");
        cas(A, "+33612345678", "Bonjour, votre compte Wave sera bloqué dans les 24h. Confirmez votre identité ici : http://192.168.10.5/wave");
        cas(A, "777000111", "Wave: transfert de 30000F reçu de 776665544. Je me suis trompé de numéro, renvoie-moi ça stp");
        cas(A, null, "Vous avez gagné 1 000 000 FCFA ! Pour recevoir votre gain, payez d'abord les frais de déblocage de 10 000 F par Wave.");
        cas(A, "785551212", "Prêt rapide sans garantie de 500 000 F, remboursement facile. Frais de dossier 15 000 F à envoyer par Orange Money.");

        cas(A, "774445566", "Asalaa maalekum, maa ngi ci Wave. May ma code bi nga jot ngir ñu saafara sa compte.");

        System.out.println("== Suspects");
        cas(S, "771239876", "Doublez votre argent en 7 jours, rendement garanti. Écrivez-moi.");
        cas(S, "778889900", "Bonjour c'est moi Moussa, voici mon nouveau numéro. Envoie-moi 5000 stp");
        cas(S, null, "Félicitations, vous avez été sélectionné pour une aide de 100 000 F du gouvernement.");

        System.out.println("== Vrais messages");
        cas(R, "Wave", "Votre code de vérification Wave est 482913. Ne le partagez avec personne, même un agent Wave.");
        cas(R, "OrangeMoney", "Vous avez reçu 15000 FCFA de 771234567. Nouveau solde : 23500 FCFA. Ref : CI231103.1840.B98765");
        cas(R, "771234567", "Salut, on se voit demain à 18h au marché ?");
        cas(R, "776543210", "Envoie-moi 5000 par Wave stp je te rembourse demain");
        cas(R, "SENELEC", "Cher client, votre facture de 25000F est disponible. Payez avant le 15 pour éviter la coupure.");
        cas(R, "CBAO", "Code OTP : 123456 pour valider votre paiement de 20000 XOF. Ne communiquez jamais ce code.");
        cas(R, "770001122", "Félicitations pour ton diplôme ! On fête ça samedi ?");
        cas(R, "781112233", "Désolé j'ai supprimé ton message par erreur, tu peux le renvoyer ?");
        cas(R, "Orange", "Orange : profitez du Pass Internet 5Go à 1000F. Tapez #1234# pour en profiter.");
        cas(R, "Wave", "Wave : vous avez envoyé 10 000 F à Awa Ndiaye (77 123 45 67). Frais : 100 F.");
        cas(R, "775554433", "Le marabout arrive demain à la maison inch'Allah");
        cas(R, "776667788", "Rdv à 15h. Merci.Bonne journée");
        cas(R, "706661122", "Regarde ce site pour la recette : www.marmiton.org/recettes/thieb.aspx");
        cas(R, "Wave", "Wave ne vous demandera jamais votre code secret. N'envoyez jamais de code à quelqu'un par téléphone.");
        cas(R, null, "Dama ñëw ci kanam, bul ma xaar ci reer bi.");
        cas(R, "771113344", "Baay, yonnee ma 2000 ngir taxi bi, dinaa la ko delloo ci ngoon.");

        cas(R, "Orange", "Votre forfait expire demain. Tapez #111# pour le renouveler.");
        cas(R, "Ecobank", "Votre compte a été débité de 50 000 XOF le 03/11. Solde disponible : 120 000 XOF.");
        cas(R, "771112299", "Je t'envoie le lien du groupe : https://chat.whatsapp.com/AbCdEfGh123");
        cas(R, "772221100", "Urgent ! Rappelle-moi stp");
        cas(R, "Wave", "Vous avez reçu 5 000F de Moussa Diop. Nouveau solde : 12 300F.");
        cas(R, "773330011", "Envoie-moi le code wifi stp");
        cas(R, "Yas", "Yas : bonus de 1000F offert sur votre recharge de 5000F.");

        System.out.println();
        System.out.println(ok + " OK, " + ko + " KO");
        if (ko > 0) System.exit(1);
    }
}
