package sn.aar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Historique des alertes, expediteurs marques surs et compteur, enregistres sur le telephone uniquement. */
public final class Journal {

    private static final String PREFS = "aar";
    private static final String K_ALERTES = "alertes";
    private static final String K_SURS = "expediteurs_surs";
    private static final String K_ANALYSES = "sms_analyses";
    private static final String K_DEMANDE = "permissions_demandees";
    private static final int MAX_ALERTES = 200;

    /** Un message signale. */
    public static final class Alerte {
        public final long id;
        public final String exp;
        public final String texte;
        public final long date;

        Alerte(long id, String exp, String texte, long date) {
            this.id = id;
            this.exp = exp;
            this.texte = texte;
            this.date = date;
        }
    }

    private Journal() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray lire(Context c) {
        try {
            return new JSONArray(prefs(c).getString(K_ALERTES, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized Alerte ajouter(Context c, String exp, String texte, long date) {
        JSONArray a = lire(c);
        long id = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("id") >= id) id = o.optLong("id") + 1;
        }
        JSONArray out = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("exp", exp == null ? "" : exp);
            o.put("texte", texte == null ? "" : texte);
            o.put("date", date);
            out.put(o);
        } catch (JSONException ignore) {
            // ne peut pas arriver avec des types simples
        }
        for (int i = 0; i < a.length() && out.length() < MAX_ALERTES; i++) out.put(a.opt(i));
        prefs(c).edit().putString(K_ALERTES, out.toString()).apply();
        return new Alerte(id, exp == null ? "" : exp, texte == null ? "" : texte, date);
    }

    /** Alertes, de la plus recente a la plus ancienne. */
    public static synchronized List<Alerte> alertes(Context c) {
        JSONArray a = lire(c);
        List<Alerte> out = new ArrayList<Alerte>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            out.add(new Alerte(o.optLong("id"), o.optString("exp", ""), o.optString("texte", ""), o.optLong("date")));
        }
        return out;
    }

    public static synchronized Alerte alerte(Context c, long id) {
        for (Alerte a : alertes(c)) if (a.id == id) return a;
        return null;
    }

    public static synchronized void supprimer(Context c, long id) {
        JSONArray a = lire(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("id") != id) out.put(o);
        }
        prefs(c).edit().putString(K_ALERTES, out.toString()).apply();
    }

    public static synchronized void effacer(Context c) {
        prefs(c).edit().putString(K_ALERTES, "[]").apply();
    }

    /** Cle stable d'un expediteur : chiffres seuls pour un numero (9 derniers), nom en minuscules sinon. */
    static String cle(String exp) {
        if (exp == null) return "";
        String s = exp.trim();
        StringBuilder d = new StringBuilder();
        boolean lettre = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetter(ch)) lettre = true;
            if (Character.isDigit(ch)) d.append(ch);
        }
        if (lettre) return s.toLowerCase();
        String n = d.toString();
        return n.length() > 9 ? n.substring(n.length() - 9) : n;
    }

    public static synchronized boolean estSur(Context c, String exp) {
        String k = cle(exp);
        return !k.isEmpty() && prefs(c).getStringSet(K_SURS, new HashSet<String>()).contains(k);
    }

    public static synchronized void marquerSur(Context c, String exp, boolean sur) {
        String k = cle(exp);
        if (k.isEmpty()) return;
        Set<String> s = new HashSet<String>(prefs(c).getStringSet(K_SURS, new HashSet<String>()));
        if (sur) s.add(k); else s.remove(k);
        prefs(c).edit().putStringSet(K_SURS, s).apply();
    }

    public static synchronized int nombreSurs(Context c) {
        return prefs(c).getStringSet(K_SURS, new HashSet<String>()).size();
    }

    public static synchronized void oublierSurs(Context c) {
        prefs(c).edit().remove(K_SURS).apply();
    }

    public static synchronized void compterAnalyse(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(K_ANALYSES, p.getInt(K_ANALYSES, 0) + 1).apply();
    }

    public static synchronized int analyses(Context c) {
        return prefs(c).getInt(K_ANALYSES, 0);
    }

    public static boolean permissionsDemandees(Context c) {
        return prefs(c).getBoolean(K_DEMANDE, false);
    }

    public static void noterDemande(Context c) {
        prefs(c).edit().putBoolean(K_DEMANDE, true).apply();
    }
}
