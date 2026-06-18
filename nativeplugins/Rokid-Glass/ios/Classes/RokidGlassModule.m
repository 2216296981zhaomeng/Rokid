#import "RokidGlassModule.h"
#import "DCUniConvert.h"

#if __has_include("RokidCXRLUniPlugin-Swift.h")
#import "RokidCXRLUniPlugin-Swift.h"
#define ROKID_GLASS_SWIFT_HEADER_AVAILABLE 1
#elif __has_include(<RokidCXRLUniPlugin/RokidCXRLUniPlugin-Swift.h>)
#import <RokidCXRLUniPlugin/RokidCXRLUniPlugin-Swift.h>
#define ROKID_GLASS_SWIFT_HEADER_AVAILABLE 1
#elif __has_include("RokidGlass-Swift.h")
#import "RokidGlass-Swift.h"
#define ROKID_GLASS_SWIFT_HEADER_AVAILABLE 1
#elif __has_include("Rokid_Glass-Swift.h")
#import "Rokid_Glass-Swift.h"
#define ROKID_GLASS_SWIFT_HEADER_AVAILABLE 1
#elif __has_include(<RokidGlass/RokidGlass-Swift.h>)
#import <RokidGlass/RokidGlass-Swift.h>
#define ROKID_GLASS_SWIFT_HEADER_AVAILABLE 1
#endif

#ifndef ROKID_GLASS_SWIFT_HEADER_AVAILABLE
@interface RokidGlassBridge : NSObject
+ (RokidGlassBridge *)sharedInstance;
- (void)setEventCallback:(UniModuleKeepAliveCallback)callback;
- (void)initSDK:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)checkPermissions:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)requestAuthorization:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)connectCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)connectCustomApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)openCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)updateCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)closeCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)queryApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)openApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)stopApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)changeAudioSceneId:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)startAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)stopAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)startPhoneAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)stopPhoneAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)isBluetoothConnected:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)requestSystemInfo:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)requestGlassDeviceInfo:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)getState:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)handleOpenURL:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
- (void)releaseSession:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback;
@end
#endif

@interface RokidCXRLModule : RokidGlassModule
@end

@implementation RokidCXRLModule
@end

@interface RokidGlassModule ()
@property (nonatomic, strong) RokidGlassBridge *bridge;
@end

@implementation RokidGlassModule

UNI_EXPORT_METHOD(@selector(setEventCallback:))
UNI_EXPORT_METHOD(@selector(initSDK:callback:))
UNI_EXPORT_METHOD(@selector(checkPermissions:callback:))
UNI_EXPORT_METHOD(@selector(requestAuthorization:callback:))
UNI_EXPORT_METHOD(@selector(connectCustomView:callback:))
UNI_EXPORT_METHOD(@selector(connectCustomApp:callback:))
UNI_EXPORT_METHOD(@selector(openCustomView:callback:))
UNI_EXPORT_METHOD(@selector(updateCustomView:callback:))
UNI_EXPORT_METHOD(@selector(closeCustomView:callback:))
UNI_EXPORT_METHOD(@selector(queryApp:callback:))
UNI_EXPORT_METHOD(@selector(openApp:callback:))
UNI_EXPORT_METHOD(@selector(stopApp:callback:))
UNI_EXPORT_METHOD(@selector(changeAudioSceneId:callback:))
UNI_EXPORT_METHOD(@selector(startAudioRecord:callback:))
UNI_EXPORT_METHOD(@selector(stopAudioRecord:callback:))
UNI_EXPORT_METHOD(@selector(startPhoneAudioRecord:callback:))
UNI_EXPORT_METHOD(@selector(stopPhoneAudioRecord:callback:))
UNI_EXPORT_METHOD(@selector(startAudio:callback:))
UNI_EXPORT_METHOD(@selector(stopAudio:callback:))
UNI_EXPORT_METHOD(@selector(isBluetoothConnected:callback:))
UNI_EXPORT_METHOD(@selector(requestSystemInfo:callback:))
UNI_EXPORT_METHOD(@selector(requestGlassDeviceInfo:callback:))
UNI_EXPORT_METHOD(@selector(getState:callback:))
UNI_EXPORT_METHOD(@selector(handleOpenURL:callback:))
UNI_EXPORT_METHOD(@selector(release:callback:))

- (RokidGlassBridge *)bridge {
    if (!_bridge) {
        _bridge = [RokidGlassBridge sharedInstance];
    }
    return _bridge;
}

- (void)setEventCallback:(UniModuleKeepAliveCallback)callback {
    [self.bridge setEventCallback:callback];
}

- (void)initSDK:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge initSDK:options callback:callback];
}

- (void)checkPermissions:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge checkPermissions:options callback:callback];
}

- (void)requestAuthorization:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge requestAuthorization:options callback:callback];
}

- (void)connectCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge connectCustomView:options callback:callback];
}

- (void)connectCustomApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge connectCustomApp:options callback:callback];
}

- (void)openCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge openCustomView:options callback:callback];
}

- (void)updateCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge updateCustomView:options callback:callback];
}

- (void)closeCustomView:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge closeCustomView:options callback:callback];
}

- (void)queryApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge queryApp:options callback:callback];
}

- (void)openApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge openApp:options callback:callback];
}

- (void)stopApp:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge stopApp:options callback:callback];
}

- (void)changeAudioSceneId:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge changeAudioSceneId:options callback:callback];
}

- (void)startAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge startAudioRecord:options callback:callback];
}

- (void)stopAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge stopAudioRecord:options callback:callback];
}

- (void)startPhoneAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge startPhoneAudioRecord:options callback:callback];
}

- (void)stopPhoneAudioRecord:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge stopPhoneAudioRecord:options callback:callback];
}

- (void)startAudio:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge startAudioRecord:options callback:callback];
}

- (void)stopAudio:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge stopAudioRecord:options callback:callback];
}

- (void)isBluetoothConnected:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge isBluetoothConnected:options callback:callback];
}

- (void)requestSystemInfo:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge requestSystemInfo:options callback:callback];
}

- (void)requestGlassDeviceInfo:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge requestGlassDeviceInfo:options callback:callback];
}

- (void)getState:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge getState:options callback:callback];
}

- (void)handleOpenURL:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge handleOpenURL:options callback:callback];
}

- (void)release:(NSDictionary *)options callback:(UniModuleKeepAliveCallback)callback {
    [self.bridge releaseSession:options callback:callback];
}

@end
