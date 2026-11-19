#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_JAR:?Set ANDROID_JAR to the API 35 android.jar}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS to Android SDK Build Tools 35 directory}"
bash fetch-deps.sh
OUT="${1:-$PWD/out}"
mkdir -p "$OUT/classes" "$OUT/generated" "$OUT/dex"
"$BUILD_TOOLS/aapt2" compile --dir res -o "$OUT/resources.zip"
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java "$OUT/generated" -o "$OUT/base.apk" "$OUT/resources.zip"
if [[ -n "${ECJ_JAR:-}" ]]; then
    java -jar "$ECJ_JAR" -source 1.8 -target 1.8 -encoding UTF-8 -classpath "$ANDROID_JAR:lib/jsoup.jar" -d "$OUT/classes" $(find src "$OUT/generated" -name '*.java')
else
    javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR:lib/jsoup.jar" -d "$OUT/classes" $(find src "$OUT/generated" -name '*.java')
fi
( cd "$OUT/classes" && zip -qr "$OUT/classes.jar" . )
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar" lib/jsoup.jar
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes*.dex )
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [[ ! -f local-dev.keystore ]]; then
    keytool -genkeypair -keystore local-dev.keystore -storepass android -alias xunji-dev -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Xunji Local Development"
fi
"$BUILD_TOOLS/apksigner" sign --ks local-dev.keystore --ks-key-alias xunji-dev --ks-pass pass:android --key-pass pass:android --out "$OUT/Xunji-0.3.0.apk" "$OUT/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT/Xunji-0.3.0.apk"
