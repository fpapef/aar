package sn.aar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultat de l'analyse d'un message : score de 0 a 100, niveau, raisons et conseils. */
public final class Verdict {

    public static final int SUR = 0;
    public static final int SUSPECT = 1;
    public static final int ARNAQUE = 2;

    /** Un indice releve dans le message. */
    public static final class Signal {
        public final String id;
        public final int poids;
        public final String raison;
        public final String conseil;

        Signal(String id, int poids, String raison, String conseil) {
            this.id = id;
            this.poids = poids;
            this.raison = raison;
            this.conseil = conseil;
        }
    }

    public final int score;
    public final int niveau;
    public final List<Signal> signaux;

    Verdict(int score, List<Signal> signaux) {
        this.score = Math.max(0, Math.min(100, score));
        this.niveau = this.score >= 60 ? ARNAQUE : (this.score >= 30 ? SUSPECT : SUR);
        this.signaux = Collections.unmodifiableList(signaux);
    }

    public String titre() {
        if (niveau == ARNAQUE) return "Arnaque probable";
        if (niveau == SUSPECT) return "Message suspect";
        return "Rien de suspect";
    }

    /** Raisons positives (indices d'arnaque), de la plus forte a la plus faible. */
    public List<String> raisons() {
        List<String> out = new ArrayList<String>();
        for (Signal s : signaux) if (s.poids > 0 && s.raison != null) out.add(s.raison);
        return out;
    }

    /** Elements rassurants (avertissement de securite officiel, etc.). */
    public List<String> rassurants() {
        List<String> out = new ArrayList<String>();
        for (Signal s : signaux) if (s.poids < 0 && s.raison != null) out.add(s.raison);
        return out;
    }

    /** Conseils sans doublon, dans l'ordre des indices. */
    public List<String> conseils() {
        List<String> out = new ArrayList<String>();
        for (Signal s : signaux) {
            if (s.poids > 0 && s.conseil != null && !out.contains(s.conseil)) out.add(s.conseil);
        }
        if (out.isEmpty()) {
            out.add("Reste prudent : ne donne jamais un code reçu par SMS, même à quelqu'un qui dit travailler chez ton opérateur.");
        }
        return out;
    }

    public boolean aIndice(String id) {
        for (Signal s : signaux) if (s.id.equals(id)) return true;
        return false;
    }

    /** Premiere raison, pour une notification ou une ligne de liste. */
    public String raisonPrincipale() {
        List<String> r = raisons();
        return r.isEmpty() ? "Aucun indice d'arnaque" : r.get(0);
    }
}
