#!/usr/bin/env bash
# Construit l'APK d'Aar sans Gradle : aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"

# Outils du SDK Android : build-tools et plateforme les plus recents, sauf si BUILD_TOOLS / PLATFORM sont definis.
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
BT=${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)}
PLATFORM=${PLATFORM:-$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)}
ANDROID_JAR=$PLATFORM/android.jar
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}

B=build
rm -rf $B && mkdir -p $B/res $B/gen $B/classes $B/dex $B/apk
echo "== outils : build-tools $(basename "$BT"), $(basename "$PLATFORM")"

echo "== ressources"
"$BT/aapt2" compile --dir res -o $B/res/compiled.zip
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -R $B/res/compiled.zip \
  --java $B/gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -o $B/apk/base.apk --auto-add-overlay

echo "== java"
find src $B/gen -name "*.java" > $B/sources.txt
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -Xlint:-options \
  -d $B/classes @$B/sources.txt
test -f $B/classes/sn/aar/MainActivity.class

echo "== dex"
(cd $B/classes && jar cf ../classes.jar .)
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output $B/dex $B/classes.jar
test -f $B/dex/classes.dex

echo "== assemblage"
cp $B/apk/base.apk $B/apk/unsigned.apk
(cd $B/dex && zip -q -X -D ../apk/unsigned.apk classes.dex)

echo "== alignement + signature"
"$BT/zipalign" -p -f 4 $B/apk/unsigned.apk $B/apk/aligned.apk
# Cle de signature : jamais publiee. Mot de passe dans la variable KS_PASS.
KS=${KEYSTORE:-keystore/aar.jks}
: "${KS_PASS:?Definis KS_PASS (mot de passe de la cle de signature)}"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -alias aar -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Aar, O=Khadim Fall, C=SN"
fi
OUT="Aar-$VERSION_NAME.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias aar --ks-pass env:KS_PASS --key-pass env:KS_PASS \
  --out "$OUT" $B/apk/aligned.apk
"$BT/apksigner" verify --verbose --print-certs "$OUT" | grep -E "Verified using v2|certificate SHA-256" | head -3
"$BT/aapt2" dump badging "$OUT" | grep -E "^(package|sdkVersion|targetSdkVersion|uses-permission|application-label:)" || true
ls -la "$OUT"
