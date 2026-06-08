# Rokid-Glass iOS Plugin Build Notes

The iOS bridge source is in `nativeplugins/Rokid-Glass/ios/Classes`.

## Current Active iOS Package

The plugin is now a single cross-platform `Rokid-Glass` native plugin:

- Android uses `com.zhaiwo.agent.rokid.RokidGlassModule` from the AAR bridge.
- iOS uses the prebuilt `RokidCXRLModule` inside `RokidCXRLUniPlugin.framework`.

The iOS framework set was copied from `F:\glass\Rokid-CXRL` into `nativeplugins/Rokid-Glass/ios`:

- `RokidCXRLUniPlugin.framework`
- `RGCxrClient.framework`
- `RGCoreKit.framework`
- `CocoaLumberjack.framework`

The JS layer still calls `uni.requireNativePlugin('Rokid-Glass')` on both platforms. `utils/rokidGlass.js` maps Android method names to the official iOS method names internally.

## Real iOS SDK Implementation

The previous Swift bridge and copied `RGCxrClient.framework` are preserved under:

- `F:\glass\Rokid-Glass-ios-real.disabled-agent-app3\Classes\RokidGlassBridge.swift.disabled`
- `F:\glass\Rokid-Glass-ios-real.disabled-agent-app3\Classes\RokidGlassPluginProxy.swift.disabled`
- `F:\glass\Rokid-Glass-ios-real.disabled-agent-app3\RGCxrClient.framework.disabled`
- `F:\glass\Rokid-Glass-ios-real.disabled-agent-app3\Podfile.disabled`

Do not rename these files back inside the active plugin. The active iOS implementation now comes from `F:\glass\Rokid-CXRL`.

Important: `RGCxrClient.framework` imports `RGCoreKit`. Both dynamic frameworks must stay in `nativeplugins/Rokid-Glass/ios` and in `frameworks`/`embedFrameworks` in `package.json`; otherwise the app can launch-crash before any Vue page runs.

## Enabling Real iOS

Build a wrapper framework on macOS first. If the error is similar to one of these, the wrapper/dependencies are incomplete:

- `RokidGlass-Swift.h file not found`
- `No such module RGCxrClient`
- `No such module RGCoreKit`
- `Undefined symbols for architecture arm64`
- launch crash: `dyld: Library not loaded: ... RGCoreKit ...`

Build steps on macOS:

1. Create an iOS Framework target named `RokidGlass`.
2. Set iOS Deployment Target to `16.0`.
3. Set Product Module Name to `RokidGlass`.
4. Enable `BUILD_LIBRARY_FOR_DISTRIBUTION = YES`.
5. Add these files to the framework target:
   - `ios/Classes/RokidGlassModule.h`
   - `ios/Classes/RokidGlassModule.m`
   - `ios/Classes/RokidGlassBridge.swift`
   - `ios/Classes/RokidGlassPluginProxy.swift`
6. Add CocoaPods dependencies from Rokid sample:
   - `pod 'RGCxrClient'`
   - `pod 'RGCoreKit'`
7. Build Release for a real iPhone device.
8. Copy the built `RokidGlass.framework` and the resolved dynamic dependencies to `nativeplugins/Rokid-Glass/ios/`.
9. Add every copied dynamic framework to `frameworks` and `embedFrameworks` in `nativeplugins/Rokid-Glass/package.json`. At minimum this usually means `RokidGlass.framework`, `RGCxrClient.framework`, and `RGCoreKit.framework`.
10. Replace the active Objective-C safety stub with the real module export and restore the URL callback hook.

The source code intentionally keeps the exported JS method names aligned with Android:

- `requestAuthorization`
- `connectCustomView`
- `openCustomView`
- `updateCustomView`
- `startAudioRecord`
- `stopAudioRecord`
- `takePhoto`
- `sendCustomCommand`
- `startVideoRecord`
- `stopVideoRecord`

Realtime teleprompter audio uses `audioChunk` events with 16 kHz, 16-bit, mono PCM base64 payload.
