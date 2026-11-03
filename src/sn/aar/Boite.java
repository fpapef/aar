package sn.aar;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import sn.aar.core.Detecteur;
import sn.aar.core.Verdict;

/** Passe en revue les derniers SMS de la boite de reception. */
public final class Boite {

    public static final class Resultat {
        public final String exp;
        public final String texte;
        public final long date;
        public final Verdict verdict;

        Resultat(String exp, String texte, long date, Verdict verdict) {
            this.exp = exp;
            this.texte = texte;
            this.date = date;
            this.verdict = verdict;
        }
    }

    public static final class Bilan {
        public int analyses;
        public int arnaques;
        public int suspects;
        public final List<Resultat> signales = new ArrayList<Resultat>();
    }

    private Boite() {}

    /** Analyse les {@code max} SMS les plus recents. A appeler hors du fil principal. */
    public static Bilan analyser(Context c, int max) {
        Bilan b = new Bilan();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    null, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER);
            if (cur == null) return b;
            int iExp = cur.getColumnIndex(Telephony.Sms.ADDRESS);
            int iTexte = cur.getColumnIndex(Telephony.Sms.BODY);
            int iDate = cur.getColumnIndex(Telephony.Sms.DATE);
            while (cur.moveToNext() && b.analyses < max) {
                String exp = iExp >= 0 ? cur.getString(iExp) : "";
                String texte = iTexte >= 0 ? cur.getString(iTexte) : "";
                long date = iDate >= 0 ? cur.getLong(iDate) : 0;
                if (exp == null) exp = "";
                if (texte == null || texte.trim().isEmpty()) continue;
                b.analyses++;
                if (Journal.estSur(c, exp)) continue;
                Verdict v = Detecteur.analyser(exp, texte);
                if (v.niveau == Verdict.ARNAQUE) b.arnaques++;
                else if (v.niveau == Verdict.SUSPECT) b.suspects++;
                else continue;
                b.signales.add(new Resultat(exp, texte, date, v));
            }
        } catch (RuntimeException e) {
            // permission retiree ou fournisseur indisponible : on renvoie ce qu'on a
        } finally {
            if (cur != null) cur.close();
        }
        Collections.sort(b.signales, new Comparator<Resultat>() {
            @Override
            public int compare(Resultat x, Resultat y) {
                if (x.verdict.score != y.verdict.score) return y.verdict.score - x.verdict.score;
                return Long.compare(y.date, x.date);
            }
        });
        return b;
    }
}
