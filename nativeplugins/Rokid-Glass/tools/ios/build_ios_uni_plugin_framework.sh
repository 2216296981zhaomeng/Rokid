#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
IOS_DIR="$PLUGIN_DIR/ios"
CLASSES_DIR="$IOS_DIR/Classes"
BUILD_DIR="$PLUGIN_DIR/build/ios-plugin"
OBJECTS_DIR="$BUILD_DIR/objects"
FRAMEWORK_NAME="RokidCXRLUniPlugin"
FRAMEWORK_DIR="$IOS_DIR/$FRAMEWORK_NAME.framework"
HEADERS_DIR="$FRAMEWORK_DIR/Headers"
MODULES_DIR="$FRAMEWORK_DIR/Modules"
SWIFTMODULE_DIR="$MODULES_DIR/$FRAMEWORK_NAME.swiftmodule"
INCLUDE_ROOT="$SCRIPT_DIR/include"
SDK_PATH="$(xcrun --sdk iphoneos --show-sdk-path)"
TARGET="arm64-apple-ios16.0"

required_frameworks=(
  "RGCxrClient.framework"
  "RGCoreKit.framework"
  "CocoaLumberjack.framework"
)

for framework in "${required_frameworks[@]}"; do
  if [[ ! -d "$IOS_DIR/$framework" ]]; then
    echo "Missing $IOS_DIR/$framework"
    exit 1
  fi
done

rm -rf "$BUILD_DIR" "$FRAMEWORK_DIR"
mkdir -p "$OBJECTS_DIR" "$HEADERS_DIR" "$SWIFTMODULE_DIR"

COMBINED_SWIFT="$BUILD_DIR/RokidCXRLUniPlugin.swift"
cat \
  "$CLASSES_DIR/RokidGlassBridge.swift" \
  "$CLASSES_DIR/RokidGlassPluginProxy.swift" \
  > "$COMBINED_SWIFT"

echo "Compiling Swift sources for $TARGET"
xcrun swiftc \
  -target "$TARGET" \
  -sdk "$SDK_PATH" \
  -swift-version 5 \
  -O \
  -parse-as-library \
  -enable-library-evolution \
  -module-name "$FRAMEWORK_NAME" \
  -F "$IOS_DIR" \
  -I "$INCLUDE_ROOT" \
  -emit-module \
  -emit-module-path "$SWIFTMODULE_DIR/arm64-apple-ios.swiftmodule" \
  -emit-module-interface-path "$SWIFTMODULE_DIR/arm64-apple-ios.swiftinterface" \
  -emit-private-module-interface-path "$SWIFTMODULE_DIR/arm64-apple-ios.private.swiftinterface" \
  -emit-objc-header \
  -emit-objc-header-path "$HEADERS_DIR/$FRAMEWORK_NAME-Swift.h" \
  -c "$COMBINED_SWIFT" \
  -o "$OBJECTS_DIR/RokidSwift.o"

echo "Compiling Objective-C bridge"
xcrun clang \
  -target "$TARGET" \
  -isysroot "$SDK_PATH" \
  -fobjc-arc \
  -fmodules \
  -mios-version-min=16.0 \
  -F "$IOS_DIR" \
  -I "$CLASSES_DIR" \
  -I "$INCLUDE_ROOT/DCUni" \
  -I "$HEADERS_DIR" \
  -c "$CLASSES_DIR/RokidGlassModule.m" \
  -o "$OBJECTS_DIR/RokidGlassModule.o"

echo "Creating static framework archive"
xcrun libtool \
  -static \
  -o "$FRAMEWORK_DIR/$FRAMEWORK_NAME" \
  "$OBJECTS_DIR/RokidSwift.o" \
  "$OBJECTS_DIR/RokidGlassModule.o"

cat > "$HEADERS_DIR/$FRAMEWORK_NAME.h" <<'EOF'
#import <Foundation/Foundation.h>

FOUNDATION_EXPORT double RokidCXRLUniPluginVersionNumber;
FOUNDATION_EXPORT const unsigned char RokidCXRLUniPluginVersionString[];
EOF

cat > "$MODULES_DIR/module.modulemap" <<EOF
framework module $FRAMEWORK_NAME {
  umbrella header "$FRAMEWORK_NAME.h"
  export *

  module * { export * }
}

module $FRAMEWORK_NAME.Swift {
  header "$FRAMEWORK_NAME-Swift.h"
  requires objc
}
EOF

cat > "$FRAMEWORK_DIR/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleExecutable</key>
  <string>$FRAMEWORK_NAME</string>
  <key>CFBundleIdentifier</key>
  <string>io.dcloud.$FRAMEWORK_NAME</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$FRAMEWORK_NAME</string>
  <key>CFBundlePackageType</key>
  <string>FMWK</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>CFBundleSupportedPlatforms</key>
  <array>
    <string>iPhoneOS</string>
  </array>
  <key>MinimumOSVersion</key>
  <string>16.0</string>
</dict>
</plist>
EOF

echo "Built $FRAMEWORK_DIR"
file "$FRAMEWORK_DIR/$FRAMEWORK_NAME" || true
