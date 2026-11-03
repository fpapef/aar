package sn.aar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import sn.aar.core.Detecteur;
import sn.aar.core.Verdict;

/** Analyse chaque SMS recu et previent si le message ressemble a une arnaque. Rien ne quitte le telephone. */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts;
        try {
            parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (RuntimeException e) {
            return;
        }
        if (parts == null || parts.length == 0) return;

        // Un long SMS arrive en plusieurs morceaux : on les recolle, expediteur par expediteur.
        java.util.LinkedHashMap<String, StringBuilder> messages = new java.util.LinkedHashMap<String, StringBuilder>();
        for (SmsMessage m : parts) {
            if (m == null) continue;
            String exp = m.getDisplayOriginatingAddress();
            if (exp == null) exp = "";
            String corps = m.getDisplayMessageBody();
            if (corps == null) continue;
            StringBuilder sb = messages.get(exp);
            if (sb == null) {
                sb = new StringBuilder();
                messages.put(exp, sb);
            }
            sb.append(corps);
        }

        long maintenant = System.currentTimeMillis();
        for (java.util.Map.Entry<String, StringBuilder> e : messages.entrySet()) {
            String exp = e.getKey();
            String texte = e.getValue().toString();
            Journal.compterAnalyse(context);
            if (Journal.estSur(context, exp)) continue;
            Verdict v = Detecteur.analyser(exp, texte);
            if (v.niveau >= Verdict.SUSPECT) {
                Journal.Alerte a = Journal.ajouter(context, exp, texte, maintenant);
                Notifs.alerter(context, a, v);
            }
        }
    }
}
