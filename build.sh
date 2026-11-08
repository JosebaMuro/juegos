#!/usr/bin/env bash
# Compila el APK sin Gradle, usando solo las herramientas del SDK de Android.
# Requisitos (Ubuntu/Debian): openjdk, android-sdk-platform-23, aapt, zipalign, apksigner, dalvik-exchange
#   sudo apt install android-sdk-platform-23 aapt zipalign apksigner dalvik-exchange
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX_JAR="${DX_JAR:-/usr/lib/android-sdk/build-tools/debian/lib/dx.jar}"
SRC=app/src/main
OUT=build
KEYSTORE="${KEYSTORE:-keystore/juegos.jks}"
KS_PASS="${KS_PASS:-juegos123}"

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

echo "1/5 Recursos (aapt)"
# El manifiesto fuente no lleva "package" (estilo Gradle moderno); se lo añadimos aquí.
sed 's|<manifest |<manifest package="com.joseba.juegos" |' "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
aapt package -f -m -J "$OUT/gen" -M "$OUT/AndroidManifest.xml" -S "$SRC/res" -A "$SRC/assets" \
  -I "$ANDROID_JAR" -F "$OUT/app.unaligned.apk" \
  --min-sdk-version 21 --target-sdk-version 34 --version-code 4 --version-name 4.0

echo "2/5 Java (javac)"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "3/5 Dex (dx)"
java -jar "$DX_JAR" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add app.unaligned.apk classes.dex >/dev/null)

# Motor de IA local (llama.cpp compilado por native/build-llama.sh) como bibliotecas nativas.
if [ -d native/libs ]; then
  rm -rf "$OUT/native" && mkdir -p "$OUT/native/lib" && cp -r native/libs/. "$OUT/native/lib/"
  (cd "$OUT/native" && find lib -name '*.so' | sort | xargs aapt add ../app.unaligned.apk >/dev/null)
else
  echo "AVISO: no hay native/libs; el chat local no tendrá motor (ejecuta native/build-llama.sh)"
fi

echo "4/5 Alinear (zipalign)"
zipalign -f -p 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"

echo "5/5 Firmar (apksigner)"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias juegos -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Juegos Reunidos, O=Joseba, C=ES"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias juegos \
  --out "$OUT/JuegosReunidos.apk" "$OUT/app.aligned.apk"
apksigner verify "$OUT/JuegosReunidos.apk"
echo "APK listo: $OUT/JuegosReunidos.apk"
