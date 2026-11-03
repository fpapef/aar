#!/usr/bin/env bash
# Test sur emulateur Android : installe Aar, envoie un faux SMS d'arnaque et un vrai message,
# verifie qu'une seule alerte part, puis capture les ecrans principaux.
set -u
cd "$(dirname "$0")/.."
UI="python3 desktop-test/ui.py"
mkdir -p captures
ECHEC=0

adb install -r "$(ls Aar-*.apk | head -1)"
for p in RECEIVE_SMS READ_SMS POST_NOTIFICATIONS; do adb shell pm grant sn.aar android.permission.$p; done
adb logcat -c
adb shell am start -W -n sn.aar/.MainActivity
sleep 4
$UI capture 1-accueil

# Un SMS d'arnaque et un message normal
adb emu sms send 781234567 "Cher client Orange Money, votre compte sera suspendu. Pour le reactiver, envoyez a notre agent le code que vous allez recevoir."
sleep 3
adb emu sms send 776543210 "Salut, on se voit demain a 18h au marche ?"
sleep 8

adb shell dumpsys notification --noredact > captures/notifications.txt
NB=$(grep -c "NotificationRecord(.*pkg=sn.aar" captures/notifications.txt || true)
echo "Notifications d'Aar : $NB"
grep -A40 "pkg=sn.aar" captures/notifications.txt | grep -E "android.title=|android.text=" | head -6
if [ "$NB" -ne 1 ]; then echo "::error::Une seule alerte attendue, $NB trouvee(s)"; ECHEC=1; fi
grep -q "Arnaque probable" captures/notifications.txt || { echo "::error::Alerte « Arnaque probable » absente"; ECHEC=1; }

adb shell cmd statusbar expand-notifications
sleep 3
$UI capture 2-notification
adb shell cmd statusbar collapse
sleep 1

adb shell wm size
adb shell am start -W -n sn.aar/.MainActivity
sleep 3
$UI defiler 3
$UI capture 3-alertes
$UI toucher 781234567 || ECHEC=1
$UI capture 4-detail
adb shell input keyevent KEYCODE_BACK
sleep 2
$UI toucher "Lancer l" || ECHEC=1
sleep 6
$UI capture 5-analyse
adb shell input keyevent KEYCODE_BACK
sleep 2

# Message partage depuis une autre appli
adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Maman c est moi, j ai change de numero. Envoie-moi 20000 par Wave stp c est urgent'" \
  -n sn.aar/.MainActivity
sleep 4
$UI capture 6-verification

adb logcat -d > captures/logcat.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: sn.aar" captures/logcat.txt; then
  echo "::error::Plantage d'Aar"; grep -A25 "FATAL EXCEPTION" captures/logcat.txt | head -40; ECHEC=1
fi
exit $ECHEC
