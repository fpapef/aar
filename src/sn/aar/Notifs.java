package sn.aar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import sn.aar.core.Verdict;

/** Notifications d'alerte. */
public final class Notifs {

    static final String CANAL = "alertes";
    static final String EXTRA_ALERTE = "sn.aar.ALERTE";

    private Notifs() {}

    static void creerCanal(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel ch = new NotificationChannel(CANAL, "Alertes d'arnaque", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Prévient quand un SMS reçu ressemble à une arnaque.");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 250, 120, 250});
        ch.setLightColor(Ui.RED);
        ch.enableLights(true);
        nm.createNotificationChannel(ch);
    }

    static void alerter(Context c, Journal.Alerte a, Verdict v) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        creerCanal(c);

        Intent ouvrir = new Intent(c, MainActivity.class);
        ouvrir.putExtra(EXTRA_ALERTE, a.id);
        ouvrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, (int) (a.id & 0x7FFFFFFF), ouvrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String titre = v.niveau == Verdict.ARNAQUE ? "⚠️ Arnaque probable" : "Message suspect";
        String exp = a.exp.isEmpty() ? "un expéditeur inconnu" : a.exp;
        StringBuilder detail = new StringBuilder();
        detail.append("De ").append(exp).append(" : « ").append(resume(a.texte, 140)).append(" »");
        List<String> raisons = v.raisons();
        for (int i = 0; i < raisons.size() && i < 3; i++) detail.append("\n• ").append(raisons.get(i));
        detail.append("\nNe réponds pas et ne donne aucun code.");

        Notification n = new Notification.Builder(c, CANAL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Ui.couleur(v.niveau))
                .setContentTitle(titre + " (" + v.score + "/100)")
                .setContentText("De " + exp + " : " + v.raisonPrincipale())
                .setStyle(new Notification.BigTextStyle().bigText(detail.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setWhen(a.date)
                .setShowWhen(true)
                .build();
        try {
            nm.notify((int) (a.id & 0x7FFFFFFF), n);
        } catch (SecurityException ignore) {
            // notifications refusees : l'alerte reste visible dans l'appli
        }
    }

    static String resume(String s, int max) {
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
