#!/bin/bash
set -e
export JAVA_HOME=$HOME/android-tools/jdk
export PATH=$JAVA_HOME/bin:$PATH
SDK=$HOME/android-tools/sdk
BT=$SDK/build-tools/35.0.0
AJAR=$SDK/platforms/android-35/android.jar
PROJ=$HOME/workspace/todo-list-release/android
OUT=$PROJ/build
rm -rf $OUT; mkdir -p $OUT/classes $OUT/dex

echo "[1/6] javac"
javac -source 8 -target 8 -nowarn -cp $AJAR -d $OUT/classes \
  $PROJ/app/src/main/java/com/thanhman5/todolist/MainActivity.java

echo "[2/6] d8"
$BT/d8 --release --lib $AJAR --output $OUT/dex \
  $(find $OUT/classes -name '*.class')

echo "[3/6] aapt2 compile+link res"
mkdir -p $OUT/res-compiled
$BT/aapt2 compile --dir $PROJ/app/src/main/res -o $OUT/res-compiled/res.zip
$BT/aapt2 link -o $OUT/unsigned.apk \
  -I $AJAR \
  --manifest $PROJ/app/src/main/AndroidManifest.xml \
  -A $PROJ/app/src/main/assets \
  --no-compress \
  $OUT/res-compiled/res.zip \
  --auto-add-overlay

echo "[4/6] add classes.dex"
cd $OUT && zip -q unsigned.apk -j dex/classes.dex && cd - > /dev/null

echo "[5/6] zipalign"
$BT/zipalign -p -f 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[6/6] sign (debug key)"
if [ ! -f $HOME/android-tools/debug.keystore ]; then
  keytool -genkeypair -v -keystore $HOME/android-tools/debug.keystore \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Personal,C=VN" 2>/dev/null
fi
$BT/apksigner sign --ks $HOME/android-tools/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out $OUT/checklist.apk $OUT/aligned.apk
$BT/apksigner verify --print-certs $OUT/checklist.apk | head -4
mkdir -p $HOME/workspace/todo-list-release/dist
cp $OUT/checklist.apk $HOME/workspace/todo-list-release/dist/Checklist-Android.apk
ls -lh $HOME/workspace/todo-list-release/dist/Checklist-Android.apk
echo BUILD_OK
