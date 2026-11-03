package sn.aar;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import sn.aar.core.Detecteur;
import sn.aar.core.Verdict;

/** Ecran unique : accueil, verification d'un message, analyse de la boite, historique et detail. */
public class MainActivity extends Activity {

    private static final int ECRAN_ACCUEIL = 0;
    private static final int ECRAN_DETAIL = 1;
    private static final int ECRAN_ANALYSE = 2;
    private static final int ECRAN_HISTORIQUE = 3;

    private static final int REQ_PROTECTION = 1;
    private static final int REQ_ANALYSE = 2;
    private static final int MAX_SMS = 300;
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    private static final String[][] EXEMPLES = {
            {"+221 77 845 12 30", "Bonjour, je vous ai envoyé 25 000 F par erreur sur votre Wave. Merci de me les renvoyer svp, c'est urgent."},
            {"781234567", "Cher client Orange Money, votre compte sera suspendu. Pour le réactiver, envoyez à notre agent le code que vous allez recevoir."},
            {"763334455", "Félicitations ! Vous avez gagné 500 000 F à la tombola Wave. Pour recevoir votre lot, payez les frais de dossier de 5 000 F."},
            {"", "Maman c'est moi, j'ai changé de numéro, mon téléphone est tombé. Envoie-moi 20 000 par Wave stp c'est urgent."},
    };

    private final SimpleDateFormat dates = new SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRANCE);

    private FrameLayout racine;
    private int ecran = ECRAN_ACCUEIL;
    private int ecranPrecedent = ECRAN_ACCUEIL;
    private boolean aRafraichir;
    private int jetonAnalyse;
    private Boite.Bilan dernierBilan;
    private int exemple;

    // brouillon du champ « Verifier un message »
    private EditText champTexte;
    private EditText champExp;
    private String brouillon = "";
    private String brouillonExp = "";
    private boolean brouillonVerifie;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Notifs.creerCanal(this);
        racine = new FrameLayout(this);
        racine.setBackgroundColor(Ui.BG);
        setContentView(racine);
        traiterIntent(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntent(intent, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        garderBrouillon();
        aRafraichir = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!aRafraichir) return;
        aRafraichir = false;
        if (ecran == ECRAN_ACCUEIL) afficherAccueil();
        else if (ecran == ECRAN_HISTORIQUE) afficherHistorique();
    }

    @Override
    public void onBackPressed() {
        if (ecran != ECRAN_ACCUEIL) retour();
        else super.onBackPressed();
    }

    private void traiterIntent(Intent i, boolean premier) {
        if (i != null && i.hasExtra(Notifs.EXTRA_ALERTE)) {
            Journal.Alerte a = Journal.alerte(this, i.getLongExtra(Notifs.EXTRA_ALERTE, -1));
            i.removeExtra(Notifs.EXTRA_ALERTE);
            if (a != null) {
                ecranPrecedent = ECRAN_ACCUEIL;
                afficherDetail(a.exp, a.texte, a.date, a.id);
                return;
            }
        }
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            CharSequence partage = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
            i.setAction(Intent.ACTION_MAIN);
            if (partage != null && partage.toString().trim().length() > 0) {
                champTexte = null;
                champExp = null;
                brouillon = partage.toString().trim();
                brouillonExp = "";
                brouillonVerifie = true;
                afficherAccueil();
                return;
            }
        }
        if (premier || ecran == ECRAN_ACCUEIL) afficherAccueil();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] resultats) {
        super.onRequestPermissionsResult(code, permissions, resultats);
        if (code == REQ_ANALYSE) {
            if (accorde(Manifest.permission.READ_SMS)) afficherAnalyse();
            else toast("Sans l'autorisation de lire les SMS, Aar ne peut pas analyser ta boîte de réception.");
        } else if (code == REQ_PROTECTION && ecran == ECRAN_ACCUEIL) {
            afficherAccueil();
        }
    }

    // ------------------------------------------------------------------ accueil

    private void afficherAccueil() {
        garderBrouillon();
        ecran = ECRAN_ACCUEIL;
        ScrollView sv = scroll();
        LinearLayout col = colonne();
        sv.addView(col);

        LinearLayout tete = Ui.hbox(this);
        tete.addView(Ui.pastille(this, R.drawable.ic_shield, Ui.ACCENT_SOFT, Ui.ACCENT, 50, 28));
        LinearLayout titres = Ui.vbox(this);
        titres.setPadding(dp(14), 0, 0, 0);
        titres.addView(Ui.title(this, "Aar"));
        titres.addView(Ui.caption(this, "Détecteur d'arnaques SMS"));
        tete.addView(titres, Ui.weight(1));
        tete.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 20));
        col.addView(tete);

        col.addView(carteProtection());
        col.addView(statistiques());
        col.addView(carteVerifier(sv));
        col.addView(carteAnalyse());
        sectionAlertes(col);

        col.addView(Ui.section(this, "Les règles d'or"));
        col.addView(carteRegles());

        TextView pied = Ui.caption(this, "« Aar » veut dire « protéger » en wolof. Tout se passe sur ton téléphone : "
                + "Aar n'a pas accès à Internet et n'envoie tes messages nulle part.");
        pied.setGravity(Gravity.CENTER);
        pied.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 8, 10, 8, 0));
        col.addView(pied);

        afficher(sv);
    }

    private View carteProtection() {
        boolean sms = accorde(Manifest.permission.RECEIVE_SMS);
        boolean notif = Build.VERSION.SDK_INT < 33 || accorde(POST_NOTIFICATIONS);
        boolean actif = sms && notif;
        int couleur = actif ? Ui.ACCENT : Ui.ORANGE;

        LinearLayout c = Ui.card(this);
        c.setBackground(Ui.roundedStroke(actif ? Ui.ACCENT_SOFT : Ui.ORANGE_SOFT, (couleur & 0x00FFFFFF) | 0x55000000, dp(1), dp(22)));
        LinearLayout ligne = Ui.hbox(this);
        ligne.addView(Ui.pastille(this, actif ? R.drawable.ic_shield : R.drawable.ic_warning, couleur, Ui.BG, 40, 22));
        TextView t = Ui.bold(this, actif ? "Protection active" : "Protection désactivée", 18, couleur);
        t.setPadding(dp(14), 0, 0, 0);
        ligne.addView(t, Ui.weight(1));
        c.addView(ligne);

        String texte;
        if (actif) {
            texte = "Aar vérifie chaque SMS reçu et te prévient tout de suite s'il ressemble à une arnaque.";
        } else if (!sms) {
            texte = "Autorise Aar à lire les SMS que tu reçois pour être prévenu des arnaques. L'analyse se fait sur ton téléphone.";
        } else {
            texte = "Autorise les notifications pour recevoir les alertes d'arnaque.";
        }
        TextView b = Ui.body(this, texte);
        b.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        c.addView(b);

        if (!actif) {
            TextView bouton = Ui.button(this, "Activer la protection", true);
            bouton.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
            clic(bouton, new Runnable() {
                @Override
                public void run() {
                    demanderProtection();
                }
            });
            c.addView(bouton);
            if (Journal.permissionsDemandees(this)) {
                TextView aide = Ui.caption(this, "Android bloque l'autorisation (« paramètre restreint ») ? Ouvre les infos de l'appli Aar, "
                        + "touche le menu ⋮ puis « Autoriser les paramètres restreints », et réessaie.");
                aide.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
                c.addView(aide);
            }
        }
        return c;
    }

    private View statistiques() {
        LinearLayout l = Ui.hbox(this);
        l.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 14));
        l.addView(chiffre(Journal.analyses(this), "SMS vérifiés", Ui.TEXT), Ui.margins(Ui.weight(1), this, 0, 0, 7, 0));
        int n = Journal.alertes(this).size();
        l.addView(chiffre(n, n > 1 ? "alertes" : "alerte", n > 0 ? Ui.RED : Ui.TEXT), Ui.margins(Ui.weight(1), this, 7, 0, 0, 0));
        return l;
    }

    private View chiffre(int n, String libelle, int couleur) {
        LinearLayout c = Ui.vbox(this);
        c.setBackground(Ui.rounded(Ui.SURFACE, dp(18)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView v = Ui.bold(this, String.valueOf(n), 24, couleur);
        c.addView(v);
        c.addView(Ui.caption(this, libelle));
        return c;
    }

    private View carteVerifier(final ScrollView sv) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.heading(this, "Vérifier un message"));
        TextView aide = Ui.caption(this, "Colle un SMS ou un message WhatsApp douteux. Tu peux aussi partager un message vers Aar depuis une autre appli.");
        aide.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 0));
        c.addView(aide);

        champTexte = Ui.champ(this, "Colle le message ici…", true);
        champTexte.setText(brouillon);
        champTexte.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        c.addView(champTexte);

        champExp = Ui.champ(this, "Expéditeur (facultatif) : numéro ou nom", false);
        champExp.setText(brouillonExp);
        champExp.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        c.addView(champExp);

        LinearLayout boutons = Ui.hbox(this);
        boutons.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        TextView verifier = Ui.button(this, "Vérifier", true);
        boutons.addView(verifier, Ui.weight(1));
        TextView ex = Ui.smallButton(this, "Exemple");
        ex.setMinHeight(dp(52));
        boutons.addView(ex, Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        c.addView(boutons);

        final LinearLayout resultat = Ui.vbox(this);
        resultat.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 0));
        c.addView(resultat);

        clic(verifier, new Runnable() {
            @Override
            public void run() {
                garderBrouillon();
                brouillonVerifie = true;
                cacherClavier(champTexte);
                montrerResultat(resultat, sv);
            }
        });
        clic(ex, new Runnable() {
            @Override
            public void run() {
                String[] e = EXEMPLES[exemple++ % EXEMPLES.length];
                champExp.setText(e[0]);
                champTexte.setText(e[1]);
                garderBrouillon();
                brouillonVerifie = true;
                cacherClavier(champTexte);
                montrerResultat(resultat, sv);
            }
        });
        if (brouillonVerifie && brouillon.trim().length() > 0) montrerResultat(resultat, sv);
        return c;
    }

    private void montrerResultat(LinearLayout resultat, ScrollView sv) {
        resultat.removeAllViews();
        final String texte = brouillon.trim();
        final String exp = brouillonExp.trim();
        if (texte.isEmpty()) {
            resultat.addView(Ui.caption(this, "Colle d'abord un message à vérifier."));
            return;
        }
        final Verdict v = Detecteur.analyser(exp, texte);
        resultat.addView(carteVerdict(v));

        LinearLayout actions = Ui.hbox(this);
        actions.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
        if (v.niveau >= Verdict.SUSPECT) {
            TextView prevenir = Ui.smallButton(this, "Prévenir mes proches");
            clic(prevenir, new Runnable() {
                @Override
                public void run() {
                    partager(exp, texte);
                }
            });
            actions.addView(prevenir, Ui.margins(Ui.weight(1), this, 0, 0, 10, 0));
        }
        TextView effacer = Ui.smallButton(this, "Effacer");
        clic(effacer, new Runnable() {
            @Override
            public void run() {
                champTexte.setText("");
                champExp.setText("");
                brouillon = "";
                brouillonExp = "";
                brouillonVerifie = false;
                afficherAccueil();
            }
        });
        actions.addView(effacer, v.niveau >= Verdict.SUSPECT ? Ui.wrap() : Ui.weight(1));
        resultat.addView(actions);

        if (sv != null) defilerVers(sv, resultat);
    }

    private View carteAnalyse() {
        LinearLayout c = Ui.card(this);
        LinearLayout l = Ui.hbox(this);
        l.addView(Ui.pastille(this, R.drawable.ic_inbox, Ui.SURFACE2, Ui.ACCENT, 40, 22));
        TextView h = Ui.heading(this, "Analyser mes SMS");
        h.setPadding(dp(14), 0, 0, 0);
        l.addView(h, Ui.weight(1));
        c.addView(l);
        TextView b = Ui.body(this, "Aar passe en revue tes " + MAX_SMS + " derniers SMS et te montre ceux qui ressemblent à une arnaque.");
        b.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 14));
        c.addView(b);
        TextView bouton = Ui.button(this, "Lancer l'analyse", false);
        clic(bouton, new Runnable() {
            @Override
            public void run() {
                lancerAnalyse();
            }
        });
        c.addView(bouton);
        return c;
    }

    private void sectionAlertes(LinearLayout col) {
        List<Journal.Alerte> alertes = Journal.alertes(this);
        LinearLayout tete = Ui.hbox(this);
        tete.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 6));
        TextView h = Ui.heading(this, "Dernières alertes");
        h.setPadding(dp(4), 0, 0, 0);
        tete.addView(h, Ui.weight(1));
        if (!alertes.isEmpty()) {
            TextView tout = Ui.lien(this, "Tout voir");
            clic(tout, new Runnable() {
                @Override
                public void run() {
                    afficherHistorique();
                }
            });
            tete.addView(tout);
        }
        col.addView(tete);

        if (alertes.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.body(this, "Aucune alerte pour l'instant. Quand un SMS douteux arrive, il s'affiche ici."));
            col.addView(c);
            return;
        }
        for (int i = 0; i < alertes.size() && i < 5; i++) col.addView(ligneAlerte(alertes.get(i), ECRAN_ACCUEIL));
    }

    private View carteRegles() {
        LinearLayout c = Ui.card(this);
        String[] regles = {
                "Ne donne jamais un code reçu par SMS, à personne.",
                "Un « transfert par erreur » ne se renvoie pas : vérifie d'abord ton solde dans l'appli.",
                "Wave, Orange Money et les banques ne t'écrivent pas depuis un numéro ordinaire.",
                "On ne paie pas de frais pour recevoir un gain, un prêt ou un colis.",
                "Un proche au « nouveau numéro » ? Appelle-le sur l'ancien avant d'envoyer de l'argent.",
                "Ne compose jamais un code *…# envoyé par un inconnu.",
        };
        for (String r : regles) c.addView(Ui.puce(this, r, Ui.ACCENT));
        return c;
    }

    // ------------------------------------------------------------------ verdict

    private View carteVerdict(Verdict v) {
        int couleur = Ui.couleur(v.niveau);
        LinearLayout c = Ui.vbox(this);
        c.setBackground(Ui.roundedStroke(Ui.couleurDouce(v.niveau), (couleur & 0x00FFFFFF) | 0x66000000, dp(1), dp(20)));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 14));

        LinearLayout tete = Ui.hbox(this);
        tete.addView(Ui.pastille(this, Ui.icone(v.niveau), couleur, Ui.BG, 44, 26));
        LinearLayout titres = Ui.vbox(this);
        titres.setPadding(dp(14), 0, 0, 0);
        titres.addView(Ui.bold(this, v.titre(), 20, couleur));
        titres.addView(Ui.caption(this, "Risque : " + v.score + " / 100"));
        tete.addView(titres, Ui.weight(1));
        c.addView(tete);
        c.addView(Ui.jauge(this, v.score, couleur));

        List<String> raisons = v.raisons();
        if (!raisons.isEmpty()) {
            c.addView(sousTitre("Pourquoi ?"));
            for (String r : raisons) c.addView(Ui.puce(this, r, couleur));
        }
        List<String> rassurants = v.rassurants();
        if (!rassurants.isEmpty()) {
            c.addView(sousTitre("Ce qui rassure"));
            for (String r : rassurants) c.addView(Ui.puce(this, r, Ui.ACCENT));
        }
        c.addView(sousTitre(v.niveau == Verdict.SUR ? "Bon réflexe" : "Que faire ?"));
        for (String r : v.conseils()) c.addView(Ui.puce(this, r, Ui.TEXT2));
        return c;
    }

    private TextView sousTitre(String s) {
        TextView t = Ui.bold(this, s, 15, Ui.TEXT);
        t.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 14, 0, 4));
        return t;
    }

    private View ligne(final String exp, String texte, long date, Verdict v, Runnable action) {
        int couleur = Ui.couleur(v.niveau);
        LinearLayout r = Ui.hbox(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(dp(14), dp(14), dp(14), dp(14));
        r.setBackground(Ui.ripple(Ui.rounded(Ui.SURFACE, dp(18)), 0x22FFFFFF));
        r.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 8));
        r.addView(Ui.pastille(this, Ui.icone(v.niveau), Ui.couleurDouce(v.niveau), couleur, 40, 22));

        LinearLayout txt = Ui.vbox(this);
        txt.setPadding(dp(14), 0, 0, 0);
        LinearLayout haut = Ui.hbox(this);
        TextView qui = Ui.bold(this, exp.isEmpty() ? "Expéditeur inconnu" : exp, 15, Ui.TEXT);
        qui.setSingleLine(true);
        haut.addView(qui, Ui.weight(1));
        if (date > 0) haut.addView(Ui.caption(this, dates.format(new Date(date))));
        txt.addView(haut);
        TextView raison = Ui.text(this, v.titre() + " · " + v.raisonPrincipale(), 14, couleur);
        raison.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 3, 0, 3));
        txt.addView(raison);
        txt.addView(Ui.extrait(this, texte, 2));
        r.addView(txt, Ui.weight(1));
        clic(r, action);
        return r;
    }

    private View ligneAlerte(final Journal.Alerte a, final int depuis) {
        return ligne(a.exp, a.texte, a.date, Detecteur.analyser(a.exp, a.texte), new Runnable() {
            @Override
            public void run() {
                ecranPrecedent = depuis;
                afficherDetail(a.exp, a.texte, a.date, a.id);
            }
        });
    }

    // ------------------------------------------------------------------ detail

    private void afficherDetail(final String exp, final String texte, final long date, final long alerteId) {
        garderBrouillon();
        ecran = ECRAN_DETAIL;
        ScrollView sv = scroll();
        LinearLayout col = colonne();
        sv.addView(col);
        col.addView(barre("Détail du message"));

        Verdict v = Detecteur.analyser(exp, texte);

        LinearLayout bulle = Ui.card(this);
        String qui = exp.isEmpty() ? "Expéditeur inconnu" : "De " + exp;
        bulle.addView(Ui.caption(this, date > 0 ? qui + " · " + dates.format(new Date(date)) : qui));
        TextView msg = Ui.text(this, texte, 16, Ui.TEXT);
        msg.setTextIsSelectable(true);
        msg.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        bulle.addView(msg);
        col.addView(bulle);

        col.addView(carteVerdict(v));

        if (v.niveau >= Verdict.SUSPECT) {
            TextView prevenir = Ui.button(this, "Prévenir mes proches", true);
            prevenir.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
            clic(prevenir, new Runnable() {
                @Override
                public void run() {
                    partager(exp, texte);
                }
            });
            col.addView(prevenir);
        }

        LinearLayout rangee = Ui.hbox(this);
        rangee.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10));
        TextView copier = Ui.smallButton(this, "Copier le message");
        clic(copier, new Runnable() {
            @Override
            public void run() {
                copier(texte);
            }
        });
        rangee.addView(copier, Ui.weight(1));
        if (Detecteur.typeExpediteur(exp) != Detecteur.EXP_INCONNU) {
            final boolean sur = Journal.estSur(this, exp);
            TextView marquer = Ui.smallButton(this, sur ? "Ne plus faire confiance" : "Expéditeur sûr");
            clic(marquer, new Runnable() {
                @Override
                public void run() {
                    Journal.marquerSur(MainActivity.this, exp, !sur);
                    toast(sur ? "Les messages de " + exp + " seront de nouveau vérifiés."
                            : "Les messages de " + exp + " ne déclencheront plus d'alerte.");
                    afficherDetail(exp, texte, date, alerteId);
                }
            });
            rangee.addView(marquer, Ui.margins(Ui.weight(1), this, 10, 0, 0, 0));
        }
        col.addView(rangee);

        if (alerteId >= 0) {
            TextView suppr = Ui.smallButton(this, "Supprimer l'alerte");
            suppr.setLayoutParams(Ui.matchWrap());
            clic(suppr, new Runnable() {
                @Override
                public void run() {
                    Journal.supprimer(MainActivity.this, alerteId);
                    toast("Alerte supprimée.");
                    retour();
                }
            });
            col.addView(suppr);
        }

        if (v.niveau >= Verdict.SUSPECT && Detecteur.typeExpediteur(exp) != Detecteur.EXP_INCONNU) {
            TextView note = Ui.caption(this, "Aar se trompe ? Touche « Expéditeur sûr » : ses prochains messages ne déclencheront plus d'alerte.");
            note.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 4, 14, 4, 0));
            col.addView(note);
        }
        afficher(sv);
    }

    // ------------------------------------------------------------------ analyse de la boite

    private void lancerAnalyse() {
        if (accorde(Manifest.permission.READ_SMS)) {
            afficherAnalyse();
            return;
        }
        if (Journal.permissionsDemandees(this) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) {
            ouvrirReglages();
            return;
        }
        Journal.noterDemande(this);
        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_ANALYSE);
    }

    private void afficherAnalyse() {
        garderBrouillon();
        ecran = ECRAN_ANALYSE;
        dernierBilan = null;
        ScrollView sv = scroll();
        LinearLayout col = colonne();
        sv.addView(col);
        col.addView(barre("Analyse de mes SMS"));

        LinearLayout attente = Ui.card(this);
        LinearLayout l = Ui.hbox(this);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        l.addView(pb, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = Ui.body(this, "Analyse de tes " + MAX_SMS + " derniers SMS…");
        t.setPadding(dp(14), 0, 0, 0);
        l.addView(t, Ui.weight(1));
        attente.addView(l);
        col.addView(attente);
        afficher(sv);

        final int jeton = ++jetonAnalyse;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Boite.Bilan b = Boite.analyser(app, MAX_SMS);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jeton != jetonAnalyse || ecran != ECRAN_ANALYSE || isFinishing()) return;
                        dernierBilan = b;
                        afficherBilan(b);
                    }
                });
            }
        }, "aar-analyse").start();
    }

    private void afficherBilan(Boite.Bilan b) {
        ecran = ECRAN_ANALYSE;
        ScrollView sv = scroll();
        LinearLayout col = colonne();
        sv.addView(col);
        col.addView(barre("Analyse de mes SMS"));

        int niveau = b.arnaques > 0 ? Verdict.ARNAQUE : (b.suspects > 0 ? Verdict.SUSPECT : Verdict.SUR);
        int couleur = Ui.couleur(niveau);
        LinearLayout c = Ui.card(this);
        c.setBackground(Ui.roundedStroke(Ui.couleurDouce(niveau), (couleur & 0x00FFFFFF) | 0x55000000, dp(1), dp(22)));
        LinearLayout tete = Ui.hbox(this);
        tete.addView(Ui.pastille(this, Ui.icone(niveau), couleur, Ui.BG, 44, 26));
        String titre;
        String detail;
        if (b.analyses == 0) {
            titre = "Aucun SMS trouvé";
            detail = "Ta boîte de réception est vide, ou Aar n'a pas pu la lire.";
        } else if (b.signales.isEmpty()) {
            titre = "Aucun SMS suspect";
            detail = "Parmi tes " + b.analyses + " derniers SMS, aucun ne ressemble à une arnaque.";
        } else {
            int n = b.signales.size();
            titre = n + (n > 1 ? " messages à surveiller" : " message à surveiller");
            detail = pluriel(b.arnaques, "arnaque probable", "arnaques probables") + " et "
                    + pluriel(b.suspects, "message suspect", "messages suspects") + " parmi tes " + b.analyses + " derniers SMS.";
        }
        TextView t = Ui.bold(this, titre, 18, couleur);
        t.setPadding(dp(14), 0, 0, 0);
        tete.addView(t, Ui.weight(1));
        c.addView(tete);
        TextView d = Ui.body(this, detail);
        d.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 12, 0, 0));
        c.addView(d);
        col.addView(c);

        for (final Boite.Resultat r : b.signales) {
            col.addView(ligne(r.exp, r.texte, r.date, r.verdict, new Runnable() {
                @Override
                public void run() {
                    ecranPrecedent = ECRAN_ANALYSE;
                    afficherDetail(r.exp, r.texte, r.date, -1);
                }
            }));
        }

        TextView relancer = Ui.button(this, "Relancer l'analyse", false);
        relancer.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
        clic(relancer, new Runnable() {
            @Override
            public void run() {
                afficherAnalyse();
            }
        });
        col.addView(relancer);
        afficher(sv);
    }

    // ------------------------------------------------------------------ historique

    private void afficherHistorique() {
        garderBrouillon();
        ecran = ECRAN_HISTORIQUE;
        ScrollView sv = scroll();
        LinearLayout col = colonne();
        sv.addView(col);
        col.addView(barre("Historique des alertes"));

        List<Journal.Alerte> alertes = Journal.alertes(this);
        if (alertes.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.body(this, "Aucune alerte enregistrée."));
            col.addView(c);
        }
        for (Journal.Alerte a : alertes) col.addView(ligneAlerte(a, ECRAN_HISTORIQUE));

        if (!alertes.isEmpty()) {
            TextView effacer = Ui.smallButton(this, "Effacer l'historique");
            effacer.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 8, 0, 0));
            clic(effacer, new Runnable() {
                @Override
                public void run() {
                    Journal.effacer(MainActivity.this);
                    toast("Historique effacé.");
                    afficherHistorique();
                }
            });
            col.addView(effacer);
        }
        final int surs = Journal.nombreSurs(this);
        if (surs > 0) {
            TextView oublier = Ui.smallButton(this, "Oublier les expéditeurs sûrs (" + surs + ")");
            oublier.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 10, 0, 0));
            clic(oublier, new Runnable() {
                @Override
                public void run() {
                    Journal.oublierSurs(MainActivity.this);
                    toast("Tous les expéditeurs seront de nouveau vérifiés.");
                    afficherHistorique();
                }
            });
            col.addView(oublier);
        }
        afficher(sv);
    }

    // ------------------------------------------------------------------ navigation et outils

    private void retour() {
        if (ecran == ECRAN_DETAIL) {
            if (ecranPrecedent == ECRAN_ANALYSE && dernierBilan != null) {
                afficherBilan(dernierBilan);
                return;
            }
            if (ecranPrecedent == ECRAN_HISTORIQUE) {
                afficherHistorique();
                return;
            }
        }
        afficherAccueil();
    }

    private View barre(String titre) {
        LinearLayout l = Ui.hbox(this);
        l.setLayoutParams(Ui.margins(Ui.matchWrap(), this, 0, 4, 0, 18));
        FrameLayout dos = Ui.iconButton(this, R.drawable.ic_back, 44);
        dos.setContentDescription("Retour");
        clic(dos, new Runnable() {
            @Override
            public void run() {
                retour();
            }
        });
        l.addView(dos);
        TextView t = Ui.heading(this, titre);
        t.setTextSize(20);
        t.setPadding(dp(14), 0, 0, 0);
        l.addView(t, Ui.weight(1));
        return l;
    }

    private void afficher(View v) {
        racine.removeAllViews();
        racine.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private ScrollView scroll() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        return sv;
    }

    private LinearLayout colonne() {
        LinearLayout col = Ui.vbox(this);
        col.setPadding(dp(20), dp(16), dp(20), dp(36));
        return col;
    }

    private void defilerVers(final ScrollView sv, final View cible) {
        sv.post(new Runnable() {
            @Override
            public void run() {
                int y = 0;
                View v = cible;
                while (v != null && v != sv) {
                    y += v.getTop();
                    if (!(v.getParent() instanceof View)) break;
                    v = (View) v.getParent();
                }
                sv.smoothScrollTo(0, Math.max(0, y - dp(120)));
            }
        });
    }

    private void garderBrouillon() {
        if (ecran != ECRAN_ACCUEIL || champTexte == null || champExp == null) return;
        brouillon = champTexte.getText().toString();
        brouillonExp = champExp.getText().toString();
    }

    private void demanderProtection() {
        List<String> manque = new ArrayList<String>();
        if (!accorde(Manifest.permission.RECEIVE_SMS)) manque.add(Manifest.permission.RECEIVE_SMS);
        if (!accorde(Manifest.permission.READ_SMS)) manque.add(Manifest.permission.READ_SMS);
        if (Build.VERSION.SDK_INT >= 33 && !accorde(POST_NOTIFICATIONS)) manque.add(POST_NOTIFICATIONS);
        if (manque.isEmpty()) {
            afficherAccueil();
            return;
        }
        // Apres deux refus, Android ne montre plus la question : seul l'ecran des reglages peut encore accorder l'autorisation.
        boolean bloque = Journal.permissionsDemandees(this);
        for (String p : manque) if (shouldShowRequestPermissionRationale(p)) bloque = false;
        if (bloque) {
            ouvrirReglages();
            return;
        }
        Journal.noterDemande(this);
        requestPermissions(manque.toArray(new String[0]), REQ_PROTECTION);
    }

    private void ouvrirReglages() {
        toast("Autorise les SMS et les notifications pour Aar dans les réglages.");
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
        } catch (RuntimeException ignore) {
            // pas d'ecran de reglages : rien a faire
        }
    }

    private void partager(String exp, String texte) {
        StringBuilder s = new StringBuilder();
        s.append("⚠️ Attention, arnaque par SMS en circulation :\n\n« ").append(texte.trim()).append(" »\n\n");
        if (exp.length() > 0) s.append("Expéditeur : ").append(exp).append("\n");
        s.append("Ne répondez pas, ne donnez aucun code et n'envoyez pas d'argent.\n— Repéré avec Aar");
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s.toString());
        try {
            startActivity(Intent.createChooser(i, "Prévenir mes proches"));
        } catch (RuntimeException e) {
            toast("Aucune appli pour partager.");
        }
    }

    private void copier(String texte) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Message", texte));
            toast("Message copié.");
        }
    }

    private void cacherClavier(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private boolean accorde(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void clic(View v, final Runnable action) {
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                action.run();
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static String pluriel(int n, String un, String plusieurs) {
        return n + " " + (n > 1 ? plusieurs : un);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
