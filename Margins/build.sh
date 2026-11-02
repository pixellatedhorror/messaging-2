#!/usr/bin/env bash
# Builds Margins.apk without Gradle: aapt2 -> javac -> dx -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
BT=${BT:-/usr/lib/android-sdk/build-tools/debian}
ANDROID_JAR=${ANDROID_JAR:-/tmp/ap/android-34/android.jar}
OUT=build
rm -rf "$OUT"; mkdir -p "$OUT"/{res,gen,classes,dex}

"$BT/aapt2" compile --dir res -o "$OUT/res/compiled.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  -A assets --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 "$OUT/res/compiled.zip"

javac -nowarn -source 8 -target 8 -encoding UTF-8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')

"$BT/dx" --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" "$OUT/classes"

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=${KS:-release.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass margins -keypass margins -alias margins \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Margins" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:margins --key-pass pass:margins \
  --ks-key-alias margins --out Margins.apk "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose Margins.apk | head -5
ls -la Margins.apk
