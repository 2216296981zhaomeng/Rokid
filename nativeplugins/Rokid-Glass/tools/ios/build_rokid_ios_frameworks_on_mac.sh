#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -d "$ROOT/ios_cxr_l_sample/ios_cxr_l_sample" ]]; then
  SAMPLE_DIR="$ROOT/ios_cxr_l_sample/ios_cxr_l_sample"
elif [[ -d "$ROOT/../ios_cxr_l_sample/ios_cxr_l_sample" ]]; then
  SAMPLE_DIR="$ROOT/../ios_cxr_l_sample/ios_cxr_l_sample"
elif [[ -d "$ROOT/../../ios_cxr_l_sample/ios_cxr_l_sample" ]]; then
  SAMPLE_DIR="$ROOT/../../ios_cxr_l_sample/ios_cxr_l_sample"
else
  echo "Cannot find ios_cxr_l_sample/ios_cxr_l_sample next to this script."
  exit 1
fi

DERIVED_DATA="$ROOT/build/DerivedData"
DIST_DIR="$ROOT/dist"
OUT_DIR="$DIST_DIR/ios-frameworks"
OUT_ZIP="$DIST_DIR/rokid-ios-frameworks.zip"

rm -rf "$DERIVED_DATA" "$OUT_DIR" "$OUT_ZIP"
mkdir -p "$OUT_DIR" "$DIST_DIR"

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild is missing. Install Xcode and select it with xcode-select."
  exit 1
fi

if ! command -v pod >/dev/null 2>&1; then
  echo "CocoaPods is missing. Install with: sudo gem install cocoapods"
  exit 1
fi

echo "Using sample: $SAMPLE_DIR"
cd "$SAMPLE_DIR"

pod install --repo-update

xcodebuild \
  -workspace "$SAMPLE_DIR/CXRClientDemo.xcworkspace" \
  -scheme CXRClientDemo \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$DERIVED_DATA" \
  CODE_SIGNING_ALLOWED=NO \
  BUILD_LIBRARY_FOR_DISTRIBUTION=YES \
  SKIP_INSTALL=NO \
  clean build

PRODUCTS_DIR="$DERIVED_DATA/Build/Products/Release-iphoneos"

copy_framework() {
  local name="$1"
  local found=""
  found="$(find "$PRODUCTS_DIR" "$SAMPLE_DIR/Pods" -type d -name "$name.framework" 2>/dev/null | head -n 1 || true)"
  if [[ -z "$found" ]]; then
    echo "Missing $name.framework"
    exit 1
  fi
  echo "Copying $name.framework from $found"
  rm -rf "$OUT_DIR/$name.framework"
  rsync -a --delete "$found/" "$OUT_DIR/$name.framework/"
  if [[ ! -f "$OUT_DIR/$name.framework/$name" ]]; then
    echo "$name.framework binary is missing after copy"
    exit 1
  fi
}

copy_framework "RGCxrClient"
copy_framework "RGCoreKit"
copy_framework "CocoaLumberjack"

if command -v otool >/dev/null 2>&1; then
  otool -L "$OUT_DIR/RGCxrClient.framework/RGCxrClient" > "$DIST_DIR/otool-RGCxrClient.txt" || true
  otool -L "$OUT_DIR/RGCoreKit.framework/RGCoreKit" > "$DIST_DIR/otool-RGCoreKit.txt" || true
  otool -L "$OUT_DIR/CocoaLumberjack.framework/CocoaLumberjack" > "$DIST_DIR/otool-CocoaLumberjack.txt" || true
fi

cd "$OUT_DIR"
/usr/bin/zip -qry "$OUT_ZIP" .

echo "Built frameworks:"
find "$OUT_DIR" -maxdepth 2 -type f -perm -111 -print
echo "Output zip: $OUT_ZIP"
