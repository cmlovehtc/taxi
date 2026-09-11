#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TAXI_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$TAXI_SDK_ROOT" ]]; then
  echo 'Set ANDROID_HOME to an installed Android SDK.' >&2
  exit 1
fi
TAXI_TOOLS="$TAXI_SDK_ROOT/build-tools/35.0.0"
TAXI_ANDROID_JAR="$TAXI_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/assets/question-bank build/generated build/classes build/dex build/tests
cp ../question-bank-manifest.json build/assets/question-bank/
python3 - <<'PY'
import pathlib, json, hashlib, shutil
root = pathlib.Path('..')
manifest = json.loads((root/'question-bank-manifest.json').read_text())
for name, expected in manifest['sha256'].items():
    path = root/name
    assert path.name == name and name.endswith('.txt')
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, name
    shutil.copyfile(path, pathlib.Path('build/assets/question-bank')/name)
print(f'Bundling {manifest["files"]} verified files, {manifest["questions"]} questions.')
PY
javac -encoding UTF-8 -d build/tests src/tw/cmlove/taxiexam/QuizCore.java tests/CoreTest.java
java -cp build/tests CoreTest build/assets/question-bank | tee build/core-test-report.txt
"$TAXI_TOOLS/aapt2" compile --dir res -o build/resources.zip
"$TAXI_TOOLS/aapt2" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$TAXI_ANDROID_JAR" --java build/generated -A build/assets build/resources.zip
javac -encoding UTF-8 --release 8 -classpath "$TAXI_ANDROID_JAR" -d build/classes $(find src build/generated -name '*.java' -print)
jar cf build/classes.jar -C build/classes .
"$TAXI_TOOLS/d8" --lib "$TAXI_ANDROID_JAR" --min-api 26 --output build/dex build/classes.jar
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
"$TAXI_TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
TAXI_TEST_KEY="${TAXI_TEST_KEYSTORE:-$HOME/.android/taxi-preview.keystore}"
mkdir -p "$(dirname "$TAXI_TEST_KEY")"
if [[ ! -f "$TAXI_TEST_KEY" ]]; then
  keytool -genkeypair -noprompt -keystore "$TAXI_TEST_KEY" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Taxi Exam Preview,O=Android,C=TW' -keyalg RSA -keysize 2048 -validity 10000
fi
"$TAXI_TOOLS/apksigner" sign --ks "$TAXI_TEST_KEY" --ks-pass pass:android --key-pass pass:android --out build/taxi-exam-0.1.0.apk build/aligned.apk
"$TAXI_TOOLS/apksigner" verify --verbose --print-certs build/taxi-exam-0.1.0.apk | tee build/signature-report.txt
"$TAXI_TOOLS/aapt" dump badging build/taxi-exam-0.1.0.apk > build/package-report.txt
sha256sum build/taxi-exam-0.1.0.apk > build/SHA256SUMS.txt
echo 'Created android-app/build/taxi-exam-0.1.0.apk'
