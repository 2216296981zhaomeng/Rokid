package com.zhaiwo.agent.rokid;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rokid.cxr.Caps;
import com.rokid.cxr.link.CXRLink;
import com.rokid.cxr.link.callbacks.IAudioStreamCbk;
import com.rokid.cxr.link.callbacks.ICXRLinkCbk;
import com.rokid.cxr.link.callbacks.ICustomCmdCbk;
import com.rokid.cxr.link.callbacks.ICustomViewCbk;
import com.rokid.cxr.link.callbacks.IGlassAppCbk;
import com.rokid.cxr.link.callbacks.IImageStreamCbk;
import com.rokid.cxr.link.utils.CxrDefs;
import com.rokid.cxr.link.utils.GlassInfo;
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult;
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper;
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniDestroyableModule;

public class RokidGlassModule extends UniDestroyableModule {
    private static final int OK = 0;
    private static final int ERR_CONTEXT = 1001;
    private static final int ERR_AUTH = 1002;
    private static final int ERR_LINK = 1003;
    private static final int ERR_STATE = 1004;
    private static final int ERR_IO = 1005;
    private static final int ERR_UNSUPPORTED = 1006;
    private static final int AUTH_REQUEST_CODE = 1001;
    private static final String NATIVE_UPLOAD_LOG_TAG = "RokidGlassNativeUpload";
    private static final String BRIDGE_VERSION = "android-cxrl-1.0.5-native-upload-autostop-20260606";
    private static final String DEFAULT_APP_PACKAGE = "com.rokid.cxrswithcxrl";
    private static final String DEFAULT_APP_ENTRY = ".activities.main.MainActivity";
    private static final GlassPermission[] REQUESTED_GLASS_PERMISSIONS = new GlassPermission[] {
            GlassPermission.MICROPHONE,
            GlassPermission.CAMERA,
            GlassPermission.MEDIA
    };
    private static final String AUTH_PERMISSION_HINT =
            "Rokid glass permission is missing. Please re-authorize in Rokid AI App with Trust unverified app enabled and microphone/camera/media permissions granted.";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object AUDIO_LOCK = new Object();
    private static final Object AUDIO_STOP_LOCK = new Object();
    private static final Object REALTIME_AUDIO_LOCK = new Object();
    private static final long CUSTOM_VIEW_OPEN_POLL_MS = 200L;
    private static final ExecutorService AUDIO_EVENT_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "RokidGlassAudioEvent");
            thread.setDaemon(true);
            return thread;
        }
    });

    private static CXRLink cxrLink;
    private static String token = "";
    private static String sessionType = "";
    private static String appPackageName = DEFAULT_APP_PACKAGE;
    private static boolean cxrConnected = false;
    private static boolean glassBtConnected = false;
    private static boolean sceneReady = false;
    private static boolean aiAssistRunning = false;
    private static boolean lastAiInterrupt = false;
    private static boolean lastSdkGlassBtConnected = false;
    private static boolean lastSdkCustomViewOpen = false;
    private static String lastSdkWearingCheckOn = "unknown";
    private static long lastRuntimeDiagnosticsAt = 0L;
    private static boolean audioStarted = false;
    private static boolean photoTaking = false;
    private static String glassDeviceName = "";
    private static String glassDeviceSn = "";
    private static String glassSystemVersion = "";
    private static String glassWearingStatus = "";
    private static String glassInfoRaw = "";
    private static int glassBatteryLevel = -1;
    private static int glassSound = -1;
    private static int glassBrightness = -1;
    private static boolean glassCharging = false;
    private static boolean bluetoothConnectPermissionRequested = false;
    private static int audioCodecType = -1;

    private static File pcmFile;
    private static File wavFile;
    private static File audioDiagFile;
    private static FileOutputStream pcmOutputStream;
    private static FileOutputStream audioDiagOutputStream;
    private static long pcmDataSize = 0L;
    private static long audioChunkCount = 0L;
    private static long firstAudioCallbackAtMs = 0L;
    private static long lastAudioCallbackAtMs = 0L;
    private static ByteArrayOutputStream realtimeAudioBuffer = new ByteArrayOutputStream();
    private static long realtimeAudioSequence = 0L;
    private static long audioSessionId = 0L;
    private static final int REALTIME_AUDIO_CHUNK_BYTES = 6400;
    private static boolean audioDebugEnabled = false;
    private static boolean nativeAudioUploadEnabled = false;
    private static long nativeAudioEnqueuedBytes = 0L;
    private static long nativeAudioDroppedBytes = 0L;
    private static long nativeAudioSentBytes = 0L;
    private static long nativeAudioSentChunks = 0L;
    private static String nativeAudioUploadState = "idle";
    private static String nativeAudioUploadError = "";

    private static class PcmLevelStats {
        int sampleCount;
        double avgAbs;
        double rms;
        int maxAbs;
        int nonZeroSamples;
        int nonZeroBytes;
        String firstBytesHex = "";
    }

    private UniJSCallback eventCallback;
    private UniJSCallback authCallback;
    private UniJSCallback pendingPhotoCallback;
    private ICXRLinkCbk linkCbk;
    private ICustomViewCbk customViewCbk;
    private IGlassAppCbk glassAppCbk;
    private IAudioStreamCbk audioStreamCbk;
    private IImageStreamCbk imageStreamCbk;
    private ICustomCmdCbk customCmdCbk;
    private NativeAudioUploader nativeAudioUploader;

    @UniJSMethod(uiThread = true)
    public void setEventCallback(UniJSCallback callback) {
        eventCallback = callback;
        invokeKeepAlive(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void initSDK(JSONObject options, UniJSCallback callback) {
        Context context = context();
        if (context == null) {
            invoke(callback, error(ERR_CONTEXT, "Android context is unavailable"));
            return;
        }
        JSONObject data = stateJson();
        data.put("rokidAIAppInstalled", isRokidAppAvailable());
        if (options != null) {
            appPackageName = stringOption(options, "packageName", appPackageName);
            String nextToken = stringOption(options, "token", "");
            if (!nextToken.isEmpty()) {
                connectSession(
                    stringOption(options, "sessionType", "customView"),
                    nextToken,
                    appPackageName,
                    callback
                );
                return;
            }
        }
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void checkPermissions(JSONObject options, UniJSCallback callback) {
        JSONObject data = stateJson();
        data.put("rokidAIAppInstalled", isRokidAppAvailable());
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void requestAuthorization(JSONObject options, UniJSCallback callback) {
        Activity activity = activity();
        if (activity == null) {
            invoke(callback, error(ERR_CONTEXT, "Authorization requires an Activity context"));
            return;
        }
        if (!isRokidAppAvailable()) {
            invoke(callback, error(ERR_AUTH, "Rokid AI App is not installed"));
            return;
        }
        authCallback = callback;
        try {
            Pair<Integer, Intent> directResult = AuthorizationHelper.INSTANCE.requestAuthorization(
                    activity,
                    REQUESTED_GLASS_PERMISSIONS,
                    AUTH_REQUEST_CODE
            );
            if (directResult != null) {
                handleAuthorizationResult(directResult.first, directResult.second);
            }
        } catch (Exception e) {
            authCallback = null;
            invoke(callback, error(ERR_AUTH, e.getMessage() == null ? "Rokid authorization failed" : e.getMessage()));
        }
    }

    @UniJSMethod(uiThread = true)
    public void connectCustomView(JSONObject options, UniJSCallback callback) {
        connectSession("customView", requireToken(options), stringOption(options, "packageName", appPackageName), callback);
    }

    @UniJSMethod(uiThread = true)
    public void connectCustomApp(JSONObject options, UniJSCallback callback) {
        connectSession("customApp", requireToken(options), stringOption(options, "packageName", appPackageName), callback);
    }

    @UniJSMethod(uiThread = true)
    public void openCustomView(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        if (!"customView".equals(sessionType)) {
            invoke(callback, error(ERR_STATE, "Current session is not customView"));
            return;
        }
        if (!hasGlassPermission(GlassPermission.MEDIA)) {
            invoke(callback, error(ERR_AUTH, glassPermissionHint()));
            return;
        }
        if (isCustomViewOpen()) {
            sceneReady = true;
            lastSdkCustomViewOpen = true;
            invoke(callback, ok(stateJson()));
            return;
        }
        String viewJson = stringOption(options, "viewJson", "");
        if (viewJson.isEmpty()) {
            viewJson = defaultCustomViewJson(stringOption(options, "title", "Rokid Glass"), stringOption(options, "text", "Ready"));
        }
        sceneReady = false;
        lastSdkCustomViewOpen = false;
        boolean accepted = cxrLink.customViewOpen(viewJson);
        if (!accepted && isCustomViewOpen()) {
            sceneReady = true;
            lastSdkCustomViewOpen = true;
            invoke(callback, ok(stateJson()));
            return;
        }
        if (!accepted) {
            invoke(callback, error(ERR_LINK, "customViewOpen request was rejected"));
            return;
        }
        int timeoutMs = intOption(options, "openWaitMs", intOption(options, "nativeTimeout", 12000) - 500);
        timeoutMs = Math.max(1500, Math.min(15000, timeoutMs));
        waitForCustomViewOpen(callback, System.currentTimeMillis(), timeoutMs);
    }

    @UniJSMethod(uiThread = true)
    public void updateCustomView(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        String updateJson = stringOption(options, "updateJson", "");
        if (updateJson.isEmpty()) {
            updateJson = "[{\"action\":\"update\",\"id\":\"textView\",\"props\":{\"text\":\""
                    + escapeJson(stringOption(options, "text", "Updated")) + "\"}}]";
        }
        boolean accepted = cxrLink.customViewUpdate(updateJson);
        invoke(callback, accepted ? ok(stateJson()) : error(ERR_LINK, "customViewUpdate request was rejected"));
    }

    @UniJSMethod(uiThread = true)
    public void closeCustomView(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        boolean accepted = cxrLink.customViewClose();
        sceneReady = false;
        invoke(callback, accepted ? ok(stateJson()) : error(ERR_LINK, "customViewClose request was rejected"));
    }

    @UniJSMethod(uiThread = true)
    public void openApp(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        if (!"customApp".equals(sessionType)) {
            invoke(callback, error(ERR_STATE, "Current session is not customApp"));
            return;
        }
        String entry = stringOption(options, "entry", appPackageName + DEFAULT_APP_ENTRY);
        cxrLink.appStart(entry, glassAppCallback());
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void stopApp(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        cxrLink.appStop(glassAppCallback());
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void queryApp(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        cxrLink.appIsInstalled(glassAppCallback());
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void installApp(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        String apkPath = stringOption(options, "apkPath", "");
        if (apkPath.isEmpty()) {
            invoke(callback, error(ERR_STATE, "apkPath is required"));
            return;
        }
        cxrLink.appUploadAndInstall(apkPath, glassAppCallback());
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void uninstallApp(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        cxrLink.appUninstall(glassAppCallback());
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void startAudioRecord(JSONObject options, UniJSCallback callback) {
        if (!ensureSceneReady(callback, options)) return;
        if ("customView".equals(sessionType)) {
            refreshRuntimeDiagnostics();
            if (!lastSdkCustomViewOpen && !isCustomViewOpen()) {
                JSONObject payload = stateJson();
                payload.put("message", "CustomView is not open, audio stream will not be started");
                emitEvent("audioStartRejected", payload);
                invoke(callback, error(ERR_STATE, "CustomView is not opened. Open customView and wait onCustomViewOpened before startAudioStream."));
                return;
            }
            sceneReady = true;
        }
        if (!glassBtConnected && !lastSdkGlassBtConnected) {
            JSONObject payload = stateJson();
            payload.put("message", "Rokid glass bluetooth is not connected");
            emitEvent("audioStartRejected", payload);
            invoke(callback, error(ERR_STATE, "Rokid glass bluetooth is not connected"));
            return;
        }
        if (!hasGlassPermission(GlassPermission.MICROPHONE)) {
            invoke(callback, error(ERR_AUTH, glassPermissionHint()));
            return;
        }
        if (audioStarted) {
            try {
                cxrLink.stopAudioStream();
            } catch (Exception ignored) {
            }
            stopNativeAudioUpload(false, 800L);
            audioStarted = false;
            closePcmOnly();
            audioSessionId++;
            resetRealtimeAudio();
        }
        int requestedCodecType = intOption(options, "codecType", 1);
        int codecType = 1;
        audioCodecType = codecType;
        if (options != null && options.containsKey("interruptAiWake")
                && boolOption(options, "interruptAiWake", false)) {
            setInterruptAiWakeInternal(true);
        }
        boolean useNativeUpload = boolOption(options, "nativeUpload", false);
        String nativeWsUrl = stringOption(options, "wsUrl", "");
        if (useNativeUpload && nativeWsUrl.isEmpty()) {
            invoke(callback, error(ERR_STATE, "wsUrl is required when nativeUpload is true"));
            return;
        }
        audioDebugEnabled = boolOption(options, "debugAudio", false);
        nativeAudioUploadEnabled = useNativeUpload;
        if (!startSavingPcm()) {
            audioCodecType = -1;
            nativeAudioUploadEnabled = false;
            invoke(callback, error(ERR_IO, "Failed to create audio file"));
            return;
        }
        if (useNativeUpload) {
            startNativeAudioUpload(options, audioSessionId);
        } else {
            stopNativeAudioUpload(false, 800L);
        }
        cxrLink.setCXRAudioCbk(audioCallback());
        refreshRuntimeDiagnostics();
        emitEvent("audioStartPreflight", stateJson());
        audioStarted = true;
        boolean accepted = cxrLink.startAudioStream(codecType);
        if (!accepted) {
            audioStarted = false;
            stopNativeAudioUpload(false, 800L);
            closePcmOnly();
            audioCodecType = -1;
            nativeAudioUploadEnabled = false;
            invoke(callback, error(ERR_LINK, "startAudioStream request was rejected"));
            return;
        }
        audioStarted = true;
        JSONObject data = stateJson();
        data.put("pcmPath", pcmFile == null ? "" : pcmFile.getAbsolutePath());
        data.put("requestedCodecType", requestedCodecType);
        data.put("nativeUpload", useNativeUpload);
        emitEvent("audioStartAccepted", data);
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void startAudio(JSONObject options, UniJSCallback callback) {
        startAudioRecord(options, callback);
    }

    @UniJSMethod(uiThread = true)
    public void stopAudioRecord(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        try {
            cxrLink.stopAudioStream();
        } catch (Exception ignored) {
        }
        JSONObject data = finalizeAudioStop("apiStop", true, 5000L);
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void stopAudio(JSONObject options, UniJSCallback callback) {
        stopAudioRecord(options, callback);
    }

    @UniJSMethod(uiThread = false)
    public void setInterruptAiWake(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        boolean enabled = boolOption(options, "enabled", boolOption(options, "interruptAiWake", false));
        boolean accepted = setInterruptAiWakeInternal(enabled);
        JSONObject data = stateJson();
        data.put("interruptAiWake", enabled);
        invoke(callback, accepted ? ok(data) : error(ERR_LINK, "setInterruptAiWake request was rejected"));
    }

    @UniJSMethod(uiThread = false)
    public void queryRuntimeDiagnostics(JSONObject options, UniJSCallback callback) {
        if (!ensureLink(callback)) return;
        refreshRuntimeDiagnostics();
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void takePhoto(JSONObject options, UniJSCallback callback) {
        if (!ensureSceneReady(callback, options)) return;
        if (!hasGlassPermission(GlassPermission.CAMERA)) {
            invoke(callback, error(ERR_AUTH, glassPermissionHint()));
            return;
        }
        if (photoTaking) {
            invoke(callback, error(ERR_STATE, "A photo request is already running"));
            return;
        }
        int width = intOption(options, "width", 1024);
        int height = intOption(options, "height", 768);
        int quality = intOption(options, "quality", 80);
        pendingPhotoCallback = callback;
        photoTaking = true;
        boolean accepted = cxrLink.takePhoto(width, height, quality);
        if (!accepted) {
            photoTaking = false;
            pendingPhotoCallback = null;
            invoke(callback, error(ERR_LINK, "takePhoto request was rejected"));
        }
    }

    @UniJSMethod(uiThread = true)
    public void sendCustomMessage(JSONObject options, UniJSCallback callback) {
        sendCustomCommand(options, callback);
    }

    @UniJSMethod(uiThread = true)
    public void sendCustomCommand(JSONObject options, UniJSCallback callback) {
        if (!ensureSceneReady(callback, options)) return;
        String key = stringOption(options, "key", "rk_custom_client");
        Caps payload = buildCapsPayload(options);
        Integer result = cxrLink.sendCustomCmd(key, payload);
        JSONObject data = stateJson();
        data.put("result", result);
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void startVideoRecord(JSONObject options, UniJSCallback callback) {
        sendVideoCommand(options, callback, "startVideoRecord");
    }

    @UniJSMethod(uiThread = true)
    public void stopVideoRecord(JSONObject options, UniJSCallback callback) {
        sendVideoCommand(options, callback, "stopVideoRecord");
    }

    @UniJSMethod(uiThread = true)
    public void isBluetoothConnected(JSONObject options, UniJSCallback callback) {
        JSONObject data = stateJson();
        data.put("connected", glassBtConnected);
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void requestSystemInfo(JSONObject options, UniJSCallback callback) {
        JSONObject data = stateJson();
        if (cxrLink != null) {
            data.put("serviceVersion", cxrLink.getServiceVersion());
            data.put("serviceVersionCode", cxrLink.getServiceVersionCode());
            requestGlassInfo();
            refreshRuntimeDiagnostics();
        }
        invoke(callback, ok(data));
    }

    @UniJSMethod(uiThread = true)
    public void requestGlassDeviceInfo(JSONObject options, UniJSCallback callback) {
        requestGlassInfo();
        refreshRuntimeDiagnostics();
        fillBluetoothNameFallback();
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void getState(JSONObject options, UniJSCallback callback) {
        if (cxrLink != null) {
            refreshRuntimeDiagnostics();
        }
        invoke(callback, ok(stateJson()));
    }

    @UniJSMethod(uiThread = true)
    public void release(JSONObject options, UniJSCallback callback) {
        if (cxrLink != null) {
            if (audioStarted || nativeAudioUploadEnabled || nativeAudioUploader != null || hasActivePcmOutput()) {
                cxrLink.stopAudioStream();
                finalizeAudioStop("release", true, 3000L);
            }
            cxrLink.disconnect();
        }
        cxrLink = null;
        token = "";
        sessionType = "";
        cxrConnected = false;
        glassBtConnected = false;
        sceneReady = false;
        audioStarted = false;
        audioCodecType = -1;
        nativeAudioUploadEnabled = false;
        audioSessionId++;
        resetRealtimeAudio();
        photoTaking = false;
        clearGlassInfo();
        invoke(callback, ok(stateJson()));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTH_REQUEST_CODE) {
            handleAuthorizationResult(resultCode, data);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void destroy() {
        stopNativeAudioUpload(false, 800L);
        eventCallback = null;
        authCallback = null;
        pendingPhotoCallback = null;
    }

    private void connectSession(String type, String connectToken, String packageName, UniJSCallback callback) {
        Context context = context();
        if (context == null) {
            invoke(callback, error(ERR_CONTEXT, "Android context is unavailable"));
            return;
        }
        if (connectToken == null || connectToken.isEmpty()) {
            invoke(callback, error(ERR_AUTH, "token is required"));
            return;
        }
        String nextType = "customApp".equals(type) ? "customApp" : "customView";
        String nextPackageName = packageName == null || packageName.isEmpty() ? DEFAULT_APP_PACKAGE : packageName;
        if (cxrLink != null
                && connectToken.equals(token)
                && nextType.equals(sessionType)
                && nextPackageName.equals(appPackageName)) {
            cxrLink.setCXRLinkCbk(linkCallback());
            cxrLink.setCXRAudioCbk(audioCallback());
            cxrLink.setCXRImageCbk(imageCallback());
            cxrLink.setCXRCustomCmdCbk(customCmdCallback());
            if ("customView".equals(nextType)) {
                cxrLink.setCXRCustomViewCbk(customViewCallback());
            }
            if (!cxrConnected) {
                boolean accepted = cxrLink.connect(connectToken);
                invoke(callback, accepted ? ok(stateJson()) : error(ERR_LINK, "connect request was rejected"));
                return;
            }
            requestGlassInfo();
            invoke(callback, ok(stateJson()));
            return;
        }
        appPackageName = nextPackageName;
        CXRLink link = new CXRLink(context);
        if ("customApp".equals(nextType)) {
            link.configCXRSession(new CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, appPackageName));
        } else {
            link.configCXRSession(new CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMVIEW));
            link.setCXRCustomViewCbk(customViewCallback());
        }
        link.setCXRLinkCbk(linkCallback());
        link.setCXRAudioCbk(audioCallback());
        link.setCXRImageCbk(imageCallback());
        link.setCXRCustomCmdCbk(customCmdCallback());
        if (cxrLink != null) {
            try {
                if (audioStarted) {
                    cxrLink.stopAudioStream();
                    stopSavingAndBuildWav();
                }
            } catch (Exception ignored) {
            }
            try {
                cxrLink.disconnect();
            } catch (Exception ignored) {
            }
        }
        cxrLink = link;
        token = connectToken;
        sessionType = nextType;
        cxrConnected = false;
        glassBtConnected = false;
        sceneReady = false;
        clearGlassInfo();
        boolean accepted = cxrLink.connect(connectToken);
        invoke(callback, accepted ? ok(stateJson()) : error(ERR_LINK, "connect request was rejected"));
    }

    private ICXRLinkCbk linkCallback() {
        if (linkCbk != null) return linkCbk;
        linkCbk = new ICXRLinkCbk() {
            @Override
            public void onCXRLConnected(boolean connected) {
                cxrConnected = connected;
                if (connected) {
                    requestGlassInfo();
                }
                emitEvent("linkStatus", stateJson());
            }

            @Override
            public void onGlassBtConnected(boolean connected) {
                glassBtConnected = connected;
                if (!connected) {
                    sceneReady = false;
                    clearGlassInfo();
                } else {
                    requestGlassInfo();
                }
                emitEvent("linkStatus", stateJson());
            }

            @Override
            public void onGlassDeviceInfo(GlassInfo glassInfo) {
                mergeGlassInfo(glassInfo);
                emitEvent("glassDeviceInfo", stateJson());
            }

            @Override
            public void onGlassWearingStatus(boolean wearing) {
                glassWearingStatus = wearing ? "wearing" : "notWearing";
                emitBooleanEvent("glassWearingStatus", "wearing", wearing);
            }

            @Override
            public void onGlassAiAssistStart() {
                aiAssistRunning = true;
                emitEvent("aiAssistStart", stateJson());
            }

            @Override
            public void onGlassAiAssistStop() {
                aiAssistRunning = false;
                emitEvent("aiAssistStop", stateJson());
            }

            @Override
            public void onGlassAiInterrupt(boolean interrupted) {
                lastAiInterrupt = interrupted;
                emitBooleanEvent("aiInterrupt", "interrupted", interrupted);
            }
        };
        return linkCbk;
    }

    private ICustomViewCbk customViewCallback() {
        if (customViewCbk != null) return customViewCbk;
        customViewCbk = new ICustomViewCbk() {
            @Override
            public void onCustomViewOpened() {
                sceneReady = true;
                refreshRuntimeDiagnostics();
                emitEvent("customViewOpened", stateJson());
            }

            @Override
            public void onCustomViewUpdated() {
                emitEvent("customViewUpdated", stateJson());
            }

            @Override
            public void onCustomViewClosed() {
                sceneReady = false;
                emitEvent("customViewClosed", stateJson());
            }

            @Override
            public void onCustomViewIconsSent() {
                emitEvent("customViewIconsSent", stateJson());
            }

            @Override
            public void onCustomViewError(int code, String message) {
                sceneReady = false;
                JSONObject data = stateJson();
                data.put("errorCode", code);
                data.put("message", message == null ? "" : message);
                emitEvent("customViewError", data);
            }
        };
        return customViewCbk;
    }

    private IGlassAppCbk glassAppCallback() {
        if (glassAppCbk != null) return glassAppCbk;
        glassAppCbk = new IGlassAppCbk() {
            @Override
            public void onInstallAppResult(boolean success) {
                emitBooleanEvent("appInstallResult", "success", success);
            }

            @Override
            public void onUnInstallAppResult(boolean success) {
                emitBooleanEvent("appUninstallResult", "success", success);
            }

            @Override
            public void onOpenAppResult(boolean success) {
                sceneReady = success;
                emitBooleanEvent("appOpenResult", "success", success);
            }

            @Override
            public void onStopAppResult(boolean success) {
                if (success) sceneReady = false;
                emitBooleanEvent("appStopResult", "success", success);
            }

            @Override
            public void onGlassAppResume(boolean resumed) {
                sceneReady = resumed;
                emitBooleanEvent("appResume", "resumed", resumed);
            }

            @Override
            public void onQueryAppResult(boolean installed) {
                emitBooleanEvent("appQueryResult", "installed", installed);
            }
        };
        return glassAppCbk;
    }

    private IAudioStreamCbk audioCallback() {
        if (audioStreamCbk != null) return audioStreamCbk;
        audioStreamCbk = new IAudioStreamCbk() {
            @Override
            public void onAudioReceived(byte[] data, int offset, int length) {
                if (data == null || length <= 0) return;
                byte[] chunk;
                long session;
                synchronized (AUDIO_LOCK) {
                    if (pcmOutputStream == null) return;
                    try {
                        long nowMs = System.currentTimeMillis();
                        int safeOffset = offset >= 0 && offset < data.length ? offset : 0;
                        int maxAvailable = data.length - safeOffset;
                        int safeLength = length > 0 && length <= maxAvailable ? length : maxAvailable;
                        if (safeLength <= 0) return;
                        pcmOutputStream.write(data, safeOffset, safeLength);
                        pcmDataSize += safeLength;
                        audioChunkCount++;
                        chunk = new byte[safeLength];
                        System.arraycopy(data, safeOffset, chunk, 0, safeLength);
                        if (firstAudioCallbackAtMs <= 0L) firstAudioCallbackAtMs = nowMs;
                        long deltaMs = lastAudioCallbackAtMs <= 0L ? 0L : nowMs - lastAudioCallbackAtMs;
                        lastAudioCallbackAtMs = nowMs;
                        if (audioDebugEnabled) {
                            writeAudioDiagnosticLineLocked(
                                    audioChunkCount,
                                    nowMs,
                                    nowMs - firstAudioCallbackAtMs,
                                    deltaMs,
                                    data.length,
                                    offset,
                                    length,
                                    safeOffset,
                                    safeLength,
                                    chunk
                            );
                        }
                        session = audioSessionId;
                        if (audioDebugEnabled && (audioChunkCount <= 3 || audioChunkCount % 20L == 0L)) {
                            pcmOutputStream.flush();
                            if (audioDiagOutputStream != null) audioDiagOutputStream.flush();
                        }
                    } catch (Exception e) {
                        JSONObject payload = stateJson();
                        payload.put("message", e.getMessage());
                        emitEvent("audioWriteError", payload);
                        return;
                    }
                }
                if (nativeAudioUploadEnabled && nativeAudioUploader != null) {
                    nativeAudioUploader.enqueue(chunk, session);
                } else {
                    enqueueRealtimeAudio(chunk, session);
                }
            }

            @Override
            public void onAudioError(int errorCode, String errorInfo) {
                int failedCodecType = audioCodecType;
                finalizeAudioStop("audioError", true, 5000L);
                JSONObject payload = stateJson();
                payload.put("audioCodecType", failedCodecType);
                payload.put("codecType", failedCodecType);
                payload.put("errorCode", errorCode);
                payload.put("message", errorInfo == null ? "" : errorInfo);
                emitEvent("audioError", payload);
            }

            @Override
            public void onAudioStreamStateChanged(boolean started) {
                int changedCodecType = audioCodecType;
                boolean hasActiveAudioFile;
                synchronized (AUDIO_LOCK) {
                    hasActiveAudioFile = pcmOutputStream != null;
                }
                if (started) {
                    audioStarted = true;
                } else {
                    audioStarted = false;
                    if (hasActiveAudioFile || nativeAudioUploadEnabled || nativeAudioUploader != null) {
                        autoFinalizeAudioStopFromSdk(changedCodecType);
                    } else {
                        audioCodecType = -1;
                    }
                }
                JSONObject payload = stateJson();
                payload.put("streamStarted", started);
                payload.put("audioFileActive", hasActiveAudioFile);
                payload.put("audioCodecType", changedCodecType);
                payload.put("codecType", changedCodecType);
                emitEvent("audioStateChanged", payload);
            }
        };
        return audioStreamCbk;
    }

    private IImageStreamCbk imageCallback() {
        if (imageStreamCbk != null) return imageStreamCbk;
        imageStreamCbk = new IImageStreamCbk() {
            @Override
            public void onImageReceived(byte[] data) {
                photoTaking = false;
                JSONObject payload = stateJson();
                try {
                    File file = savePhoto(data);
                    payload.put("path", file.getAbsolutePath());
                    payload.put("size", data == null ? 0 : data.length);
                    emitEvent("photoReceived", payload);
                    invoke(pendingPhotoCallback, ok(payload));
                } catch (Exception e) {
                    payload.put("message", e.getMessage());
                    emitEvent("photoError", payload);
                    invoke(pendingPhotoCallback, error(ERR_IO, "Failed to save photo: " + e.getMessage()));
                }
                pendingPhotoCallback = null;
            }

            @Override
            public void onImageError(int code, String message) {
                photoTaking = false;
                JSONObject payload = stateJson();
                payload.put("errorCode", code);
                payload.put("message", message == null ? "" : message);
                emitEvent("photoError", payload);
                invoke(pendingPhotoCallback, error(ERR_LINK, "takePhoto failed: " + code + " " + (message == null ? "" : message)));
                pendingPhotoCallback = null;
            }
        };
        return imageStreamCbk;
    }

    private ICustomCmdCbk customCmdCallback() {
        if (customCmdCbk != null) return customCmdCbk;
        customCmdCbk = new ICustomCmdCbk() {
            @Override
            public void onCustomCmdResult(String key, byte[] payload) {
                JSONObject data = stateJson();
                data.put("key", key == null ? "" : key);
                data.put("base64", payload == null ? "" : Base64.encodeToString(payload, Base64.NO_WRAP));
                if (payload != null) {
                    try {
                        data.put("caps", capsToJson(Caps.fromBytes(payload)));
                    } catch (Exception ignored) {
                        data.put("caps", new JSONArray());
                    }
                }
                emitEvent("customCommandResult", data);
            }
        };
        return customCmdCbk;
    }

    private void sendVideoCommand(JSONObject options, UniJSCallback callback, String defaultCommand) {
        if (!"customApp".equals(sessionType)) {
            invoke(callback, error(ERR_UNSUPPORTED, "Video recording is exposed through customApp commands; connect customApp first"));
            return;
        }
        if (options == null) options = new JSONObject();
        if (!options.containsKey("command")) {
            options.put("command", defaultCommand);
        }
        if (!options.containsKey("key")) {
            options.put("key", "rk_custom_client");
        }
        sendCustomCommand(options, callback);
    }

    private boolean ensureLink(UniJSCallback callback) {
        if (cxrLink == null) {
            invoke(callback, error(ERR_LINK, "CXRLink is not connected"));
            return false;
        }
        return true;
    }

    private boolean isCustomViewOpen() {
        if (cxrLink == null) return false;
        try {
            return cxrLink.customViewIsOpen();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void waitForCustomViewOpen(final UniJSCallback callback, final long startedAt, final int timeoutMs) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshRuntimeDiagnostics();
                if (sceneReady || lastSdkCustomViewOpen || isCustomViewOpen()) {
                    sceneReady = true;
                    lastSdkCustomViewOpen = true;
                    JSONObject data = stateJson();
                    data.put("customViewOpenConfirmed", true);
                    invoke(callback, ok(data));
                    return;
                }
                if (System.currentTimeMillis() - startedAt >= timeoutMs) {
                    JSONObject data = stateJson();
                    data.put("customViewOpenConfirmed", false);
                    emitEvent("customViewOpenTimeout", data);
                    invoke(callback, error(ERR_STATE, "customViewOpen accepted but CustomView did not open"));
                    return;
                }
                MAIN.postDelayed(this, CUSTOM_VIEW_OPEN_POLL_MS);
            }
        }, CUSTOM_VIEW_OPEN_POLL_MS);
    }

    private boolean ensureSceneReady(UniJSCallback callback, JSONObject options) {
        if (!ensureLink(callback)) return false;
        boolean skipCheck = boolOption(options, "skipSceneReadyCheck", false);
        refreshRuntimeDiagnostics();
        if ("customView".equals(sessionType) && lastSdkCustomViewOpen) {
            sceneReady = true;
        }
        if (!skipCheck && !sceneReady) {
            invoke(callback, error(ERR_STATE, "Glasses scene is not ready. Open customView or customApp first."));
            return false;
        }
        return true;
    }

    private void refreshRuntimeDiagnostics() {
        if (cxrLink == null) {
            lastSdkGlassBtConnected = false;
            lastSdkCustomViewOpen = false;
            lastSdkWearingCheckOn = "unknown";
            lastRuntimeDiagnosticsAt = System.currentTimeMillis();
            return;
        }
        try {
            lastSdkGlassBtConnected = cxrLink.isGlassBtConnected();
        } catch (Exception ignored) {
            lastSdkGlassBtConnected = false;
        }
        try {
            lastSdkCustomViewOpen = cxrLink.customViewIsOpen();
        } catch (Exception ignored) {
            lastSdkCustomViewOpen = false;
        }
        try {
            lastSdkWearingCheckOn = cxrLink.isWearingCheckOn() ? "on" : "off";
        } catch (Exception ignored) {
            lastSdkWearingCheckOn = "unknown";
        }
        lastRuntimeDiagnosticsAt = System.currentTimeMillis();
    }

    private boolean setInterruptAiWakeInternal(boolean enabled) {
        if (cxrLink == null) return false;
        try {
            return cxrLink.setInterruptAiWake(enabled);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean startSavingPcm() {
        Context context = context();
        if (context == null) return false;
        File baseDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "RokidGlass");
        if (!baseDir.exists() && !baseDir.mkdirs()) return false;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        synchronized (AUDIO_LOCK) {
            closePcmOnly();
            String codecSuffix = audioCodecType >= 0 ? "_codec" + audioCodecType : "";
            pcmFile = new File(baseDir, "rokid_" + timestamp + codecSuffix + ".pcm");
            wavFile = new File(baseDir, "rokid_" + timestamp + codecSuffix + ".wav");
            audioDiagFile = new File(baseDir, "rokid_" + timestamp + codecSuffix + "_diag.csv");
            resetRealtimeAudio();
            try {
                pcmOutputStream = new FileOutputStream(pcmFile, false);
                if (audioDebugEnabled) {
                    audioDiagOutputStream = new FileOutputStream(audioDiagFile, false);
                    writeAudioDiagnosticHeaderLocked();
                } else {
                    audioDiagOutputStream = null;
                }
                pcmDataSize = 0L;
                audioChunkCount = 0L;
                firstAudioCallbackAtMs = 0L;
                lastAudioCallbackAtMs = 0L;
                audioSessionId++;
                return true;
            } catch (Exception e) {
                closePcmOnly();
                return false;
            }
        }
    }

    private void closePcmOnly() {
        synchronized (AUDIO_LOCK) {
            try {
                if (pcmOutputStream != null) {
                    pcmOutputStream.flush();
                    pcmOutputStream.getFD().sync();
                    pcmOutputStream.close();
                }
                if (audioDiagOutputStream != null) {
                    audioDiagOutputStream.flush();
                    audioDiagOutputStream.getFD().sync();
                    audioDiagOutputStream.close();
                }
            } catch (Exception ignored) {
            }
            pcmOutputStream = null;
            audioDiagOutputStream = null;
        }
    }

    private void resetRealtimeAudio() {
        synchronized (REALTIME_AUDIO_LOCK) {
            realtimeAudioBuffer.reset();
            realtimeAudioSequence = 0L;
        }
    }

    private void enqueueRealtimeAudio(final byte[] chunk, final long session) {
        if (chunk == null || chunk.length <= 0) return;
        AUDIO_EVENT_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                if (session != audioSessionId || !audioStarted) return;
                appendRealtimeAudio(chunk, 0, chunk.length);
            }
        });
    }

    private void appendRealtimeAudio(byte[] data, int offset, int length) {
        if (data == null || length <= 0) return;
        synchronized (REALTIME_AUDIO_LOCK) {
            try {
                realtimeAudioBuffer.write(data, offset, length);
            } catch (Exception ignored) {
                return;
            }
            flushRealtimeAudioLocked(false);
        }
    }

    private void flushRealtimeAudio(boolean isFinal) {
        synchronized (REALTIME_AUDIO_LOCK) {
            flushRealtimeAudioLocked(isFinal);
        }
    }

    private void flushRealtimeAudioLocked(boolean isFinal) {
        int bufferedSize = realtimeAudioBuffer.size();
        if (bufferedSize <= 0) return;
        if (!isFinal && bufferedSize < REALTIME_AUDIO_CHUNK_BYTES) return;
        byte[] buffered = realtimeAudioBuffer.toByteArray();
        realtimeAudioBuffer.reset();
        int offset = 0;
        while (buffered.length - offset >= REALTIME_AUDIO_CHUNK_BYTES) {
            byte[] chunk = new byte[REALTIME_AUDIO_CHUNK_BYTES];
            System.arraycopy(buffered, offset, chunk, 0, REALTIME_AUDIO_CHUNK_BYTES);
            emitRealtimeAudioChunk(chunk, false);
            offset += REALTIME_AUDIO_CHUNK_BYTES;
        }
        int remaining = buffered.length - offset;
        if (remaining <= 0) return;
        if (isFinal) {
            byte[] chunk = new byte[remaining];
            System.arraycopy(buffered, offset, chunk, 0, remaining);
            emitRealtimeAudioChunk(chunk, true);
            return;
        }
        realtimeAudioBuffer.write(buffered, offset, remaining);
    }

    private void emitRealtimeAudioChunk(byte[] chunk, boolean isFinal) {
        if (chunk == null || chunk.length <= 0) return;
        realtimeAudioSequence++;
        JSONObject payload = stateJson();
        payload.put("sequence", realtimeAudioSequence);
        payload.put("audioSessionId", audioSessionId);
        payload.put("realtimeAudioSequence", realtimeAudioSequence);
        payload.put("audioChunkCount", audioChunkCount);
        payload.put("base64", Base64.encodeToString(chunk, Base64.NO_WRAP));
        payload.put("bytes", chunk.length);
        addPcmLevelStats(payload, chunk);
        payload.put("final", isFinal);
        payload.put("codec", "pcm");
        payload.put("sampleRate", 16000);
        payload.put("channels", 1);
        payload.put("bitsPerSample", 16);
        emitEvent("audioChunk", payload);
    }

    private void startNativeAudioUpload(JSONObject options, long session) {
        stopNativeAudioUpload(false, 800L);
        nativeAudioUploadEnabled = true;
        nativeAudioEnqueuedBytes = 0L;
        nativeAudioDroppedBytes = 0L;
        nativeAudioSentBytes = 0L;
        nativeAudioSentChunks = 0L;
        nativeAudioUploadError = "";
        nativeAudioUploadState = "starting";
        nativeAudioUploader = new NativeAudioUploader(options, session);
        nativeAudioUploader.start();
    }

    private void stopNativeAudioUpload(boolean sendStop, long waitMs) {
        NativeAudioUploader uploader = nativeAudioUploader;
        nativeAudioUploader = null;
        if (uploader != null) {
            uploader.stop(sendStop, waitMs);
        }
        nativeAudioUploadEnabled = false;
        if (uploader == null && !"idle".equals(nativeAudioUploadState)) {
            nativeAudioUploadState = "stopped";
        }
    }

    private boolean hasActivePcmOutput() {
        synchronized (AUDIO_LOCK) {
            return pcmOutputStream != null;
        }
    }

    private JSONObject finalizeAudioStop(String reason, boolean sendStop, long uploadWaitMs) {
        synchronized (AUDIO_STOP_LOCK) {
            int stoppedCodecType = audioCodecType;
            boolean hadActiveAudio = audioStarted || nativeAudioUploadEnabled || nativeAudioUploader != null || hasActivePcmOutput();
            audioStarted = false;
            if (nativeAudioUploadEnabled || nativeAudioUploader != null) {
                stopNativeAudioUpload(sendStop, uploadWaitMs);
            } else {
                flushRealtimeAudio(true);
            }
            if (hadActiveAudio) {
                audioSessionId++;
            }
            resetRealtimeAudio();
            JSONObject data = stopSavingAndBuildWav();
            data.put("audioCodecType", stoppedCodecType);
            data.put("codecType", stoppedCodecType);
            data.put("stopReason", reason == null ? "" : reason);
            audioCodecType = -1;
            nativeAudioUploadEnabled = false;
            audioDebugEnabled = false;
            return data;
        }
    }

    private void autoFinalizeAudioStopFromSdk(final int stoppedCodecType) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject data = finalizeAudioStop("sdkStreamStopped", true, 5000L);
                data.put("audioCodecType", stoppedCodecType);
                data.put("codecType", stoppedCodecType);
                emitEvent("audioAutoStopped", data);
            }
        }, "RokidGlassAudioAutoStop");
        thread.setDaemon(true);
        thread.start();
    }

    private static String absoluteWsUrl(String url) {
        if (url == null) return "";
        String value = url.trim();
        if (value.startsWith("ws://") || value.startsWith("wss://")) return value;
        if (value.startsWith("http://")) return "ws://" + value.substring("http://".length());
        if (value.startsWith("https://")) return "wss://" + value.substring("https://".length());
        return value;
    }

    private class NativeAudioUploader {
        private final Object queueLock = new Object();
        private final LinkedList<byte[]> queue = new LinkedList<>();
        private final JSONObject startPayload;
        private final JSONObject stopPayload;
        private final JSONObject sessionData;
        private final JSONObject headers;
        private final String wsUrl;
        private final int chunkBytes;
        private final int maxQueueBytes;
        private final long session;
        private final CountDownLatch stoppedLatch = new CountDownLatch(1);
        private volatile boolean running = true;
        private volatile boolean sendStopOnClose = false;
        private SimpleWebSocketClient socket;
        private ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private Thread readerThread;

        NativeAudioUploader(JSONObject options, long session) {
            this.session = session;
            JSONObject opts = options == null ? new JSONObject() : options;
            wsUrl = absoluteWsUrl(stringOption(opts, "wsUrl", ""));
            headers = objectOption(opts, "headers");
            sessionData = objectOption(opts, "sessionData");
            chunkBytes = Math.max(640, intOption(opts, "chunkBytes", REALTIME_AUDIO_CHUNK_BYTES));
            int queueChunks = Math.max(60, intOption(opts, "maxQueueChunks", 240));
            maxQueueBytes = Math.max(chunkBytes * 10, chunkBytes * queueChunks);
            JSONObject start = objectOption(opts, "startPayload");
            JSONObject stop = objectOption(opts, "stopPayload");
            startPayload = start.isEmpty() ? buildNativeUploadPayload("session.start", null, 0, 0, false) : start;
            stopPayload = stop.isEmpty() ? buildNativeUploadPayload("session.stop", null, 0, 0, true) : stop;
        }

        void start() {
            AUDIO_EVENT_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    runUploadLoop();
                }
            });
        }

        void enqueue(byte[] chunk, long chunkSession) {
            if (chunk == null || chunk.length <= 0) return;
            if (!running || chunkSession != session || session != audioSessionId) return;
            synchronized (queueLock) {
                queue.add(chunk);
                nativeAudioEnqueuedBytes += chunk.length;
                int total = queuedBytesLocked();
                while (total > maxQueueBytes && !queue.isEmpty()) {
                    byte[] dropped = queue.removeFirst();
                    nativeAudioDroppedBytes += dropped.length;
                    total -= dropped.length;
                }
                queueLock.notifyAll();
            }
        }

        void stop(boolean sendStop, long waitMs) {
            sendStopOnClose = sendStop;
            running = false;
            synchronized (queueLock) {
                queueLock.notifyAll();
            }
            try {
                stoppedLatch.await(Math.max(100L, waitMs), TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            SimpleWebSocketClient local = socket;
            if (local != null) local.close();
        }

        private void runUploadLoop() {
            try {
                nativeAudioUploadState = "connecting";
                socket = new SimpleWebSocketClient(wsUrl, headers);
                socket.connect(10000);
                nativeAudioUploadState = "connected";
                startReaderThread(socket);
                sendJson(startPayload);
                emitNativeUploadEvent("nativeUploadStarted", "");
                long lastStatsAt = 0L;
                while (running || hasPendingAudio()) {
                    byte[] next = pollQueueChunk(running ? 250L : 0L);
                    if (next != null) {
                        appendUploadBytes(next);
                    }
                    flushUploadChunks(false);
                    long now = System.currentTimeMillis();
                    if (now - lastStatsAt >= 1000L) {
                        lastStatsAt = now;
                        emitNativeUploadEvent("nativeUploadStats", "");
                    }
                }
                flushUploadChunks(true);
                if (sendStopOnClose) {
                    sendJson(stopPayload);
                }
                nativeAudioUploadState = "stopped";
                emitNativeUploadEvent("nativeUploadStopped", "");
            } catch (Exception e) {
                nativeAudioUploadState = "error";
                nativeAudioUploadError = e.getMessage() == null ? e.toString() : e.getMessage();
                emitNativeUploadEvent("nativeUploadError", nativeAudioUploadError);
            } finally {
                if (socket != null) {
                    socket.close();
                    socket = null;
                }
                synchronized (queueLock) {
                    queue.clear();
                }
                stoppedLatch.countDown();
            }
        }

        private boolean hasPendingAudio() {
            synchronized (queueLock) {
                return !queue.isEmpty() || pending.size() > 0;
            }
        }

        private byte[] pollQueueChunk(long waitMs) {
            synchronized (queueLock) {
                if (queue.isEmpty() && waitMs > 0L && running) {
                    try {
                        queueLock.wait(waitMs);
                    } catch (Exception ignored) {
                    }
                }
                return queue.isEmpty() ? null : queue.removeFirst();
            }
        }

        private int queuedBytesLocked() {
            int total = 0;
            for (byte[] item : queue) total += item == null ? 0 : item.length;
            return total;
        }

        private void appendUploadBytes(byte[] bytes) {
            if (bytes == null || bytes.length <= 0) return;
            try {
                pending.write(bytes, 0, bytes.length);
            } catch (Exception ignored) {
            }
        }

        private void flushUploadChunks(boolean finalFlush) throws Exception {
            int size = pending.size();
            if (size <= 0) return;
            if (!finalFlush && size < chunkBytes) return;
            byte[] all = pending.toByteArray();
            pending.reset();
            int offset = 0;
            while (all.length - offset >= chunkBytes) {
                byte[] chunk = new byte[chunkBytes];
                System.arraycopy(all, offset, chunk, 0, chunkBytes);
                sendAudioChunk(chunk, false);
                offset += chunkBytes;
            }
            int remaining = all.length - offset;
            if (remaining <= 0) return;
            if (finalFlush) {
                byte[] chunk = new byte[remaining];
                System.arraycopy(all, offset, chunk, 0, remaining);
                sendAudioChunk(chunk, true);
            } else {
                pending.write(all, offset, remaining);
            }
        }

        private void sendAudioChunk(byte[] chunk, boolean isFinal) throws Exception {
            if (chunk == null || chunk.length <= 0 || socket == null) return;
            long index = nativeAudioSentChunks + 1L;
            JSONObject payload = buildNativeUploadPayload("audio.chunk", chunk, index, chunk.length, isFinal);
            sendJson(payload);
            nativeAudioSentChunks = index;
            nativeAudioSentBytes += chunk.length;
        }

        private JSONObject buildNativeUploadPayload(String event, byte[] audio, long index, int bytes, boolean isFinal) {
            JSONObject data = new JSONObject();
            data.putAll(sessionData);
            data.put("type", event);
            data.put("messageType", event);
            data.put("audioTransport", "websocket-json-base64");
            data.put("transport", "json");
            data.put("codec", "pcm");
            data.put("format", "pcm_s16le");
            data.put("mimeType", "audio/pcm");
            data.put("sampleRate", 16000);
            data.put("channels", 1);
            data.put("bitsPerSample", 16);
            data.put("endian", "little");
            data.put("timestamp", System.currentTimeMillis());
            data.put("audioSessionId", session);
            data.put("nativeUpload", true);
            data.put("chunkBytes", bytes > 0 ? bytes : chunkBytes);
            data.put("chunkDurationMs", bytes > 0 ? Math.round(bytes / 2f / 16000f * 1000f) : 0);
            if ("audio.chunk".equals(event)) {
                PcmLevelStats stats = analyzePcmLevel(audio);
                data.put("chunkIndex", index);
                data.put("chunkSeq", index);
                data.put("bytes", bytes);
                data.put("final", isFinal);
                data.put("chunkBase64", Base64.encodeToString(audio, Base64.NO_WRAP));
                data.put("pcmAvgAbs", stats.avgAbs);
                data.put("pcmMaxAbs", stats.maxAbs);
                data.put("pcmNonZeroSamples", stats.nonZeroSamples);
                data.put("pcmNonZeroBytes", stats.nonZeroBytes);
                data.put("pcmSilentLike", stats.maxAbs <= 2);
                data.put("pcmFirstBytesHex", stats.firstBytesHex);
                data.put("pcmGain", 1);
            }
            JSONObject payload = new JSONObject();
            payload.put("event", event);
            payload.put("data", data);
            return payload;
        }

        private void sendJson(JSONObject payload) throws Exception {
            if (socket != null && payload != null) {
                socket.sendText(payload.toJSONString());
            }
        }

        private void startReaderThread(final SimpleWebSocketClient client) {
            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running && client == socket) {
                        try {
                            String message = client.readTextFrame();
                            if (message == null) break;
                            JSONObject payload = stateJson();
                            payload.put("message", message);
                            emitEvent("nativeUploadMessage", payload);
                        } catch (Exception e) {
                            if (running && client == socket) {
                                nativeAudioUploadError = e.getMessage() == null ? e.toString() : e.getMessage();
                            }
                            break;
                        }
                    }
                }
            }, "RokidGlassNativeUploadReader");
            readerThread.setDaemon(true);
            readerThread.start();
        }
    }

    private void emitNativeUploadEvent(String event, String message) {
        JSONObject payload = stateJson();
        payload.put("nativeUpload", nativeAudioUploadEnabled);
        payload.put("nativeUploadState", nativeAudioUploadState);
        payload.put("nativeUploadError", message == null || message.isEmpty() ? nativeAudioUploadError : message);
        try {
            String detail = event
                    + " state=" + nativeAudioUploadState
                    + " sentChunks=" + nativeAudioSentChunks
                    + " sentBytes=" + nativeAudioSentBytes
                    + " enqueuedBytes=" + nativeAudioEnqueuedBytes
                    + " droppedBytes=" + nativeAudioDroppedBytes;
            if (message != null && !message.isEmpty()) detail += " message=" + message;
            Log.i(NATIVE_UPLOAD_LOG_TAG, detail);
        } catch (Exception ignored) {
        }
        emitEvent(event, payload);
    }

    private JSONObject objectOption(JSONObject options, String key) {
        if (options == null || key == null || key.isEmpty()) return new JSONObject();
        try {
            JSONObject object = options.getJSONObject(key);
            return object == null ? new JSONObject() : object;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static class SimpleWebSocketClient {
        private final String url;
        private final JSONObject headers;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private volatile boolean connected = false;

        SimpleWebSocketClient(String url, JSONObject headers) {
            this.url = url;
            this.headers = headers == null ? new JSONObject() : headers;
        }

        void connect(int timeoutMs) throws Exception {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean secure = "wss".equalsIgnoreCase(scheme);
            if (!secure && !"ws".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Unsupported websocket url: " + url);
            }
            int port = uri.getPort();
            if (port <= 0) port = secure ? 443 : 80;
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Missing websocket host: " + url);
            }
            int safeTimeoutMs = Math.max(5000, timeoutMs);
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(host, port), safeTimeoutMs);
            if (secure) {
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(plainSocket, host, port, true);
                sslSocket.setUseClientMode(true);
                sslSocket.setSoTimeout(safeTimeoutMs);
                sslSocket.startHandshake();
                socket = sslSocket;
            } else {
                socket = plainSocket;
            }
            socket.setSoTimeout(safeTimeoutMs);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            String key = websocketKey();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) path += "?" + uri.getRawQuery();
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host);
            if ((secure && port != 443) || (!secure && port != 80)) request.append(":").append(port);
            request.append("\r\n");
            request.append("Upgrade: websocket\r\n");
            request.append("Connection: Upgrade\r\n");
            request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
            request.append("Sec-WebSocket-Version: 13\r\n");
            for (String headerKey : headers.keySet()) {
                if (headerKey == null || headerKey.trim().isEmpty()) continue;
                Object value = headers.get(headerKey);
                if (value == null) continue;
                request.append(headerKey).append(": ").append(String.valueOf(value)).append("\r\n");
            }
            request.append("\r\n");
            output.write(request.toString().getBytes("UTF-8"));
            output.flush();
            String response = readHttpHeader(input);
            if (response.indexOf(" 101 ") < 0 && response.indexOf(" 101\r\n") < 0) {
                throw new IllegalStateException("WebSocket handshake failed: " + firstResponseLine(response));
            }
            socket.setSoTimeout(0);
            connected = true;
        }

        synchronized void sendText(String text) throws Exception {
            if (!connected || output == null) throw new IllegalStateException("WebSocket is not connected");
            byte[] payload = text == null ? new byte[0] : text.getBytes("UTF-8");
            output.write(0x81);
            writeMaskedLengthAndPayload(payload);
            output.flush();
        }

        String readTextFrame() throws Exception {
            if (!connected || input == null) return null;
            int b0 = input.read();
            if (b0 < 0) return null;
            int b1 = input.read();
            if (b1 < 0) return null;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long length = b1 & 0x7F;
            if (length == 126) {
                int hi = input.read();
                int lo = input.read();
                if (hi < 0 || lo < 0) return null;
                length = ((long) (hi & 0xFF) << 8) | (lo & 0xFF);
            } else if (length == 127) {
                length = 0L;
                for (int i = 0; i < 8; i++) {
                    int next = input.read();
                    if (next < 0) return null;
                    length = (length << 8) | (next & 0xFF);
                }
            }
            if (length > 1024L * 1024L) throw new IllegalStateException("WebSocket frame too large");
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                readFully(input, mask, 0, mask.length);
            }
            byte[] payload = new byte[(int) length];
            readFully(input, payload, 0, payload.length);
            if (masked && mask != null) {
                for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
            if (opcode == 0x8) return null;
            if (opcode == 0x9) {
                sendPong(payload);
                return "";
            }
            if (opcode != 0x1) return "";
            return new String(payload, "UTF-8");
        }

        synchronized void close() {
            connected = false;
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
            socket = null;
            input = null;
            output = null;
        }

        private void writeMaskedLengthAndPayload(byte[] payload) throws Exception {
            int length = payload.length;
            if (length <= 125) {
                output.write(0x80 | length);
            } else if (length <= 65535) {
                output.write(0x80 | 126);
                output.write((length >> 8) & 0xFF);
                output.write(length & 0xFF);
            } else {
                output.write(0x80 | 127);
                long longLength = length;
                for (int i = 7; i >= 0; i--) {
                    output.write((int) ((longLength >> (8 * i)) & 0xFF));
                }
            }
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);
            output.write(mask);
            for (int i = 0; i < payload.length; i++) {
                output.write(payload[i] ^ mask[i % 4]);
            }
        }

        private synchronized void sendPong(byte[] payload) throws Exception {
            if (!connected || output == null) return;
            output.write(0x8A);
            writeMaskedLengthAndPayload(payload == null ? new byte[0] : payload);
            output.flush();
        }

        private static String websocketKey() {
            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            return Base64.encodeToString(nonce, Base64.NO_WRAP);
        }

        private static String readHttpHeader(InputStream input) throws Exception {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int matched = 0;
            int b;
            byte[] end = new byte[] {'\r', '\n', '\r', '\n'};
            while ((b = input.read()) >= 0) {
                buffer.write(b);
                matched = b == end[matched] ? matched + 1 : (b == end[0] ? 1 : 0);
                if (matched == end.length) break;
                if (buffer.size() > 16384) throw new IllegalStateException("WebSocket handshake response too large");
            }
            return buffer.toString("UTF-8");
        }

        private static void readFully(InputStream input, byte[] target, int offset, int length) throws Exception {
            int readTotal = 0;
            while (readTotal < length) {
                int read = input.read(target, offset + readTotal, length - readTotal);
                if (read < 0) throw new IllegalStateException("WebSocket frame ended early");
                readTotal += read;
            }
        }

        private static String firstResponseLine(String response) {
            if (response == null) return "";
            int index = response.indexOf("\r\n");
            return index >= 0 ? response.substring(0, index) : response;
        }
    }

    private void addPcmLevelStats(JSONObject payload, byte[] chunk) {
        if (payload == null || chunk == null || chunk.length < 2) return;
        PcmLevelStats stats = analyzePcmLevel(chunk);
        payload.put("sampleCount", stats.sampleCount);
        payload.put("avgAbs", stats.avgAbs);
        payload.put("rms", stats.rms);
        payload.put("maxAbs", stats.maxAbs);
        payload.put("nonZeroSamples", stats.nonZeroSamples);
        payload.put("nonZeroBytes", stats.nonZeroBytes);
        payload.put("firstBytesHex", stats.firstBytesHex);
        payload.put("silentLike", stats.maxAbs <= 2);
    }

    private PcmLevelStats analyzePcmLevel(byte[] chunk) {
        PcmLevelStats stats = new PcmLevelStats();
        if (chunk == null || chunk.length <= 0) return stats;
        int samples = chunk.length / 2;
        long sumAbs = 0L;
        double sumSquares = 0.0;
        int maxAbs = 0;
        int nonZeroSamples = 0;
        int nonZeroBytes = 0;
        for (byte b : chunk) {
            if (b != 0) nonZeroBytes++;
        }
        for (int i = 0; i + 1 < chunk.length; i += 2) {
            int sample = (short) ((chunk[i] & 0xFF) | ((chunk[i + 1] & 0xFF) << 8));
            int abs = Math.abs(sample);
            sumAbs += abs;
            sumSquares += (double) sample * (double) sample;
            if (abs > maxAbs) maxAbs = abs;
            if (sample != 0) nonZeroSamples++;
        }
        stats.sampleCount = samples;
        stats.avgAbs = samples <= 0 ? 0 : Math.round(sumAbs * 100.0 / samples) / 100.0;
        stats.rms = samples <= 0 ? 0 : Math.round(Math.sqrt(sumSquares / samples) * 100.0) / 100.0;
        stats.maxAbs = maxAbs;
        stats.nonZeroSamples = nonZeroSamples;
        stats.nonZeroBytes = nonZeroBytes;
        stats.firstBytesHex = firstBytesHex(chunk, 16);
        return stats;
    }

    private void writeAudioDiagnosticHeaderLocked() throws Exception {
        if (audioDiagOutputStream == null) return;
        String header = "seq,wallMs,elapsedMs,deltaMs,inputBytes,inputOffset,inputLength,safeOffset,safeLength,"
                + "chunkBytes,sampleCount,maxAbs,avgAbs,rms,nonZeroSamples,nonZeroBytes,firstBytesHex,"
                + "audioSessionId,audioStarted,audioCodecType,cxrConnected,glassBtConnected,sdkGlassBtConnected,"
                + "sceneReady,sdkCustomViewOpen,sdkAudioStreaming,aiAssistRunning,lastAiInterrupt,glassWearingStatus,sdkWearingCheckOn\n";
        audioDiagOutputStream.write(header.getBytes("UTF-8"));
    }

    private void writeAudioDiagnosticLineLocked(
            long sequence,
            long wallMs,
            long elapsedMs,
            long deltaMs,
            int inputBytes,
            int inputOffset,
            int inputLength,
            int safeOffset,
            int safeLength,
            byte[] chunk
    ) throws Exception {
        if (audioDiagOutputStream == null) return;
        PcmLevelStats stats = analyzePcmLevel(chunk);
        String line = sequence + ","
                + wallMs + ","
                + elapsedMs + ","
                + deltaMs + ","
                + inputBytes + ","
                + inputOffset + ","
                + inputLength + ","
                + safeOffset + ","
                + safeLength + ","
                + (chunk == null ? 0 : chunk.length) + ","
                + stats.sampleCount + ","
                + stats.maxAbs + ","
                + formatDouble(stats.avgAbs) + ","
                + formatDouble(stats.rms) + ","
                + stats.nonZeroSamples + ","
                + stats.nonZeroBytes + ","
                + stats.firstBytesHex + ","
                + audioSessionId + ","
                + audioStarted + ","
                + audioCodecType + ","
                + cxrConnected + ","
                + glassBtConnected + ","
                + lastSdkGlassBtConnected + ","
                + sceneReady + ","
                + lastSdkCustomViewOpen + ","
                + isSdkAudioStreaming() + ","
                + aiAssistRunning + ","
                + lastAiInterrupt + ","
                + csv(glassWearingStatus) + ","
                + csv(lastSdkWearingCheckOn)
                + "\n";
        audioDiagOutputStream.write(line.getBytes("UTF-8"));
    }

    private String firstBytesHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length <= 0 || maxBytes <= 0) return "";
        int count = Math.min(bytes.length, maxBytes);
        StringBuilder builder = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            int value = bytes[i] & 0xFF;
            if (value < 16) builder.append('0');
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String csv(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private boolean isSdkAudioStreaming() {
        if (cxrLink == null) return false;
        try {
            java.lang.reflect.Field field = findField(cxrLink.getClass(), "b");
            if (field == null) return false;
            field.setAccessible(true);
            Object service = field.get(cxrLink);
            if (service == null) return false;
            java.lang.reflect.Method method = service.getClass().getMethod("isAudioStreaming");
            Object result = method.invoke(service);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private java.lang.reflect.Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private JSONObject stopSavingAndBuildWav() {
        File localPcm;
        File localWav;
        long localSize;
        synchronized (AUDIO_LOCK) {
            closePcmOnly();
            localPcm = pcmFile;
            localWav = wavFile;
            localSize = pcmDataSize > 0 ? pcmDataSize : (localPcm != null && localPcm.exists() ? localPcm.length() : 0L);
        }
        JSONObject data = stateJson();
        data.put("pcmPath", localPcm == null ? "" : localPcm.getAbsolutePath());
        data.put("diagPath", audioDiagFile == null ? "" : audioDiagFile.getAbsolutePath());
        data.put("path", "");
        data.put("durationSeconds", 0);
        data.put("bytes", localSize);
        data.put("chunkCount", audioChunkCount);
        if (localPcm == null || localWav == null || localSize <= 0) {
            return data;
        }
        try {
            buildWavFromPcm(localPcm, localWav, localSize);
            data.put("path", localWav.getAbsolutePath());
            data.put("durationSeconds", localSize / (16000.0 * 2.0));
        } catch (Exception e) {
            data.put("message", e.getMessage());
        }
        return data;
    }

    private void buildWavFromPcm(File pcm, File wav, long pcmSize) throws Exception {
        int sampleRate = 16000;
        short channels = 1;
        short bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        FileOutputStream wavOut = new FileOutputStream(wav, false);
        try {
            writeWavHeader(wavOut, pcmSize, sampleRate, channels, bitsPerSample, byteRate);
            FileInputStream pcmIn = new FileInputStream(pcm);
            try {
                byte[] buffer = new byte[4096];
                int read = pcmIn.read(buffer);
                while (read > 0) {
                    wavOut.write(buffer, 0, read);
                    read = pcmIn.read(buffer);
                }
            } finally {
                pcmIn.close();
            }
        } finally {
            wavOut.close();
        }
    }

    private void writeWavHeader(FileOutputStream out, long totalAudioLen, int sampleRate, short channels, short bitsPerSample, int byteRate) throws Exception {
        long totalDataLen = totalAudioLen + 36;
        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[20] = 1; header[22] = (byte) channels;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8);
        header[34] = (byte) bitsPerSample;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

    private File savePhoto(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("empty image data");
        }
        Context context = context();
        if (context == null) {
            throw new IllegalStateException("context unavailable");
        }
        File baseDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "RokidGlass");
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IllegalStateException("cannot create photo directory");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(baseDir, "rokid_" + timestamp + ".jpg");
        FileOutputStream out = new FileOutputStream(file, false);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        return file;
    }

    private Caps buildCapsPayload(JSONObject options) {
        if (options != null) {
            String base64 = stringOption(options, "base64Payload", "");
            if (!base64.isEmpty()) {
                return Caps.fromBytes(Base64.decode(base64, Base64.DEFAULT));
            }
        }
        Caps caps = new Caps();
        String replyKey = stringOption(options, "replyKey", "rk_custom_key");
        caps.write(replyKey);
        caps.write(stringOption(options, "command", stringOption(options, "text", "")));
        JSONObject params = options == null ? null : options.getJSONObject("params");
        if (params != null && !params.isEmpty()) {
            caps.write(params.toJSONString());
        }
        return caps;
    }

    private JSONArray capsToJson(Caps caps) {
        JSONArray array = new JSONArray();
        if (caps == null) return array;
        for (int i = 0; i < caps.size(); i++) {
            Caps.Value value = caps.at(i);
            JSONObject item = new JSONObject();
            item.put("type", String.valueOf(value.type()));
            if (value.type() == Caps.Value.TYPE_STRING) {
                item.put("value", value.getString());
            } else if (value.type() == Caps.Value.TYPE_INT32 || value.type() == Caps.Value.TYPE_UINT32) {
                item.put("value", value.getInt());
            } else if (value.type() == Caps.Value.TYPE_INT64 || value.type() == Caps.Value.TYPE_UINT64) {
                item.put("value", value.getLong());
            } else if (value.type() == Caps.Value.TYPE_FLOAT) {
                item.put("value", value.getFloat());
            } else if (value.type() == Caps.Value.TYPE_DOUBLE) {
                item.put("value", value.getDouble());
            } else if (value.type() == Caps.Value.TYPE_BINARY) {
                Caps.Binary binary = value.getBinary();
                item.put("value", binary == null ? "" : Base64.encodeToString(binary.data, binary.offset, binary.length, Base64.NO_WRAP));
            } else if (value.type() == Caps.Value.TYPE_OBJECT) {
                item.put("value", capsToJson(value.getObject()));
            } else {
                item.put("value", null);
            }
            array.add(item);
        }
        return array;
    }

    private void emitBooleanEvent(String event, String key, boolean value) {
        JSONObject data = stateJson();
        data.put(key, value);
        emitEvent(event, data);
    }

    private void emitEvent(String event, JSONObject data) {
        if (eventCallback == null) return;
        if (data == null) data = new JSONObject();
        data.put("event", event);
        invokeKeepAlive(eventCallback, ok(data));
    }

    private void requestGlassInfo() {
        if (cxrLink == null) return;
        try {
            cxrLink.getGlassDeviceInfo();
        } catch (Exception ignored) {
        }
    }

    private void fillBluetoothNameFallback() {
        if (!glassDeviceName.isEmpty()) return;
        String name = connectedOrBondedRokidName();
        if (!name.isEmpty()) {
            glassDeviceName = name;
        }
    }

    private String connectedOrBondedRokidName() {
        Context ctx = context();
        if (ctx == null) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothConnectPermission();
            return "";
        }
        try {
            BluetoothManager manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager != null ? manager.getAdapter() : BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return "";
            if (manager != null) {
                String connectedName = firstRokidDeviceName(manager.getConnectedDevices(BluetoothProfile.GATT));
                if (!connectedName.isEmpty()) return connectedName;
                connectedName = firstRokidDeviceName(manager.getConnectedDevices(BluetoothProfile.GATT_SERVER));
                if (!connectedName.isEmpty()) return connectedName;
            }
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            return firstRokidDeviceName(bondedDevices);
        } catch (Exception ignored) {
        }
        return "";
    }

    private String firstRokidDeviceName(Iterable<BluetoothDevice> devices) {
        if (devices == null) return "";
        for (BluetoothDevice device : devices) {
            if (device == null) continue;
            String name = safeString(device.getName());
            if (isRokidBluetoothName(name)) return name;
        }
        return "";
    }

    private void requestBluetoothConnectPermission() {
        if (bluetoothConnectPermissionRequested) return;
        Activity act = activity();
        if (act == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        bluetoothConnectPermissionRequested = true;
        try {
            act.requestPermissions(new String[] { Manifest.permission.BLUETOOTH_CONNECT }, 2002);
        } catch (Exception ignored) {
        }
    }

    private boolean isRokidBluetoothName(String name) {
        if (name == null || name.isEmpty()) return false;
        String upper = name.toUpperCase(Locale.US);
        return upper.contains("ROKID") || upper.contains("GLASS") || upper.contains("CXR");
    }

    private void mergeGlassInfo(GlassInfo glassInfo) {
        if (glassInfo == null) return;
        glassInfoRaw = glassInfo.toString();
        glassDeviceName = safeString(glassInfo.deviceName);
        glassDeviceSn = safeString(glassInfo.sn);
        glassSystemVersion = safeString(glassInfo.systemVersion);
        glassWearingStatus = safeString(glassInfo.wearingStatus);
        glassBatteryLevel = glassInfo.batteryLevel;
        glassSound = glassInfo.sound;
        glassBrightness = glassInfo.brightness;
        glassCharging = glassInfo.ischarging;
    }

    private void clearGlassInfo() {
        glassDeviceName = "";
        glassDeviceSn = "";
        glassSystemVersion = "";
        glassWearingStatus = "";
        glassInfoRaw = "";
        glassBatteryLevel = -1;
        glassSound = -1;
        glassBrightness = -1;
        glassCharging = false;
    }

    private JSONObject glassInfoJson() {
        JSONObject info = new JSONObject();
        String id = glassDeviceName.isEmpty() ? glassDeviceSn : glassDeviceName;
        String source = glassDeviceName.isEmpty() ? (glassDeviceSn.isEmpty() ? "unavailable" : "sn") : "bluetoothName";
        info.put("glassId", id);
        info.put("glassIdSource", source);
        info.put("glassIdStable", "sn".equals(source));
        info.put("deviceId", id);
        info.put("sn", glassDeviceSn);
        info.put("deviceName", glassDeviceName);
        info.put("batteryLevel", glassBatteryLevel);
        info.put("sound", glassSound);
        info.put("brightness", glassBrightness);
        info.put("systemVersion", glassSystemVersion);
        info.put("charging", glassCharging);
        info.put("wearingStatus", glassWearingStatus);
        info.put("raw", glassInfoRaw);
        return info;
    }

    private JSONObject stateJson() {
        JSONObject data = new JSONObject();
        fillBluetoothNameFallback();
        String id = glassDeviceName.isEmpty() ? glassDeviceSn : glassDeviceName;
        String source = glassDeviceName.isEmpty() ? (glassDeviceSn.isEmpty() ? "unavailable" : "sn") : "bluetoothName";
        data.put("sessionType", sessionType);
        data.put("bridgeVersion", BRIDGE_VERSION);
        data.put("packageName", appPackageName);
        data.put("hasToken", token != null && !token.isEmpty());
        data.put("cxrConnected", cxrConnected);
        data.put("glassBtConnected", glassBtConnected);
        data.put("ready", cxrConnected && glassBtConnected);
        data.put("sceneReady", sceneReady);
        data.put("sdkGlassBtConnected", lastSdkGlassBtConnected);
        data.put("sdkCustomViewOpen", lastSdkCustomViewOpen);
        data.put("sdkWearingCheckOn", lastSdkWearingCheckOn);
        data.put("runtimeDiagnosticsAt", lastRuntimeDiagnosticsAt);
        data.put("aiAssistRunning", aiAssistRunning);
        data.put("lastAiInterrupt", lastAiInterrupt);
        data.put("audioStarted", audioStarted);
        data.put("audioCodecType", audioCodecType);
        data.put("codecType", audioCodecType);
        data.put("audioSessionId", audioSessionId);
        data.put("audioChunkCount", audioChunkCount);
        data.put("firstAudioCallbackAtMs", firstAudioCallbackAtMs);
        data.put("lastAudioCallbackAtMs", lastAudioCallbackAtMs);
        data.put("sdkAudioStreaming", isSdkAudioStreaming());
        data.put("pcmPath", pcmFile == null ? "" : pcmFile.getAbsolutePath());
        data.put("diagPath", audioDiagFile == null ? "" : audioDiagFile.getAbsolutePath());
        data.put("realtimeAudioSequence", realtimeAudioSequence);
        data.put("nativeUpload", nativeAudioUploadEnabled);
        data.put("nativeUploadState", nativeAudioUploadState);
        data.put("nativeUploadError", nativeAudioUploadError);
        data.put("nativeUploadEnqueuedBytes", nativeAudioEnqueuedBytes);
        data.put("nativeUploadDroppedBytes", nativeAudioDroppedBytes);
        data.put("nativeUploadSentBytes", nativeAudioSentBytes);
        data.put("nativeUploadSentChunks", nativeAudioSentChunks);
        data.put("audioDebugEnabled", audioDebugEnabled);
        data.put("photoTaking", photoTaking);
        data.put("glassId", id);
        data.put("glassIdSource", source);
        data.put("glassIdStable", "sn".equals(source));
        data.put("deviceId", id);
        data.put("sn", glassDeviceSn);
        data.put("deviceName", glassDeviceName);
        data.put("glassInfo", glassInfoRaw);
        data.put("glassDeviceInfo", glassInfoJson());
        data.put("callerPackageName", callerPackageName());
        data.put("callerPackageRokidPrefix", callerPackageName().startsWith("com.rokid."));
        data.put("glassPermissions", glassPermissionsJson());
        data.put("audioUsable", hasGlassPermission(GlassPermission.MICROPHONE));
        data.put("allPermissionsGranted", hasAllRequestedGlassPermissions());
        return data;
    }

    private JSONObject ok(JSONObject data) {
        JSONObject result = new JSONObject();
        result.put("code", OK);
        result.put("data", data == null ? new JSONObject() : data);
        return result;
    }

    private JSONObject error(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("message", message == null ? "" : message);
        return result;
    }

    private void invoke(final UniJSCallback callback, final JSONObject result) {
        if (callback == null) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.invoke(result);
            }
        });
    }

    private void invokeKeepAlive(final UniJSCallback callback, final JSONObject result) {
        if (callback == null) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.invokeAndKeepAlive(result);
            }
        });
    }

    private Context context() {
        return mUniSDKInstance == null ? null : mUniSDKInstance.getContext();
    }

    private Activity activity() {
        Context context = context();
        return context instanceof Activity ? (Activity) context : null;
    }

    private boolean isRokidAppAvailable() {
        Activity activity = activity();
        return activity != null && (AuthorizationHelper.INSTANCE.isRequiredRokidAppInstalled(activity)
                || AuthorizationHelper.INSTANCE.isRokidAppInstalled(activity));
    }

    private void handleAuthorizationResult(int resultCode, Intent data) {
        AuthResult result = AuthorizationHelper.INSTANCE.parseAuthorizationResult(resultCode, data);
        JSONObject payload = new JSONObject();
        if (result instanceof AuthResult.AuthSuccess) {
            token = ((AuthResult.AuthSuccess) result).getToken();
            payload.put("token", token);
            payload.put("authorized", true);
            payload.put("callerPackageName", callerPackageName());
            payload.put("callerPackageRokidPrefix", callerPackageName().startsWith("com.rokid."));
            payload.put("glassPermissions", glassPermissionsJson());
            payload.put("usable", hasGlassPermission(GlassPermission.MICROPHONE));
            payload.put("allPermissionsGranted", hasAllRequestedGlassPermissions());
            emitEvent("authorization", payload);
            invoke(authCallback, ok(payload));
        } else if (result instanceof AuthResult.AuthFail) {
            payload.put("authorized", false);
            payload.put("reason", "fail");
            emitEvent("authorization", payload);
            invoke(authCallback, error(ERR_AUTH, "Rokid authorization failed"));
        } else {
            payload.put("authorized", false);
            payload.put("reason", "cancel");
            emitEvent("authorization", payload);
            invoke(authCallback, error(ERR_AUTH, "Rokid authorization was cancelled"));
        }
        authCallback = null;
    }

    private String[] permissionNames() {
        String[] names = new String[REQUESTED_GLASS_PERMISSIONS.length];
        for (int i = 0; i < REQUESTED_GLASS_PERMISSIONS.length; i++) {
            names[i] = REQUESTED_GLASS_PERMISSIONS[i].getPermission();
        }
        return names;
    }

    private JSONObject glassPermissionsJson() {
        JSONObject permissions = new JSONObject();
        boolean microphone = hasGlassPermission(GlassPermission.MICROPHONE);
        boolean camera = hasGlassPermission(GlassPermission.CAMERA);
        boolean media = hasGlassPermission(GlassPermission.MEDIA);
        permissions.put("microphone", microphone);
        permissions.put("camera", camera);
        permissions.put("media", media);
        permissions.put("allGranted", microphone && camera && media);
        permissions.put("requested", permissionNames());
        return permissions;
    }

    private boolean hasAllRequestedGlassPermissions() {
        for (GlassPermission permission : REQUESTED_GLASS_PERMISSIONS) {
            if (!hasGlassPermission(permission)) return false;
        }
        return true;
    }

    private boolean hasGlassPermission(GlassPermission permission) {
        try {
            return AuthorizationHelper.INSTANCE.hasGlassPermission(permission);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String callerPackageName() {
        Context ctx = context();
        return ctx == null ? "" : safeString(ctx.getPackageName());
    }

    private String glassPermissionHint() {
        return AUTH_PERMISSION_HINT
                + " callerPackage=" + callerPackageName()
                + " permissions=" + glassPermissionsJson().toJSONString();
    }

    private String requireToken(JSONObject options) {
        String value = stringOption(options, "token", "");
        return value.isEmpty() ? token : value;
    }

    private String stringOption(JSONObject options, String key, String defaultValue) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) return defaultValue;
        String value = options.getString(key);
        return value == null ? defaultValue : value;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private int intOption(JSONObject options, String key, int defaultValue) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) return defaultValue;
        Integer value = options.getInteger(key);
        return value == null ? defaultValue : value;
    }

    private boolean boolOption(JSONObject options, String key, boolean defaultValue) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) return defaultValue;
        Boolean value = options.getBoolean(key);
        return value == null ? defaultValue : value;
    }

    private String defaultCustomViewJson(String title, String text) {
        return "{"
                + "\"type\":\"LinearLayout\","
                + "\"props\":{\"id\":\"root\",\"layout_width\":\"match_parent\",\"layout_height\":\"match_parent\",\"orientation\":\"vertical\",\"gravity\":\"center_horizontal\",\"marginTop\":\"160dp\",\"marginBottom\":\"80dp\",\"backgroundColor\":\"#FF000000\"},"
                + "\"children\":["
                + "{\"type\":\"TextView\",\"props\":{\"id\":\"titleView\",\"layout_width\":\"wrap_content\",\"layout_height\":\"wrap_content\",\"text\":\"" + escapeJson(title) + "\",\"textColor\":\"#FFFFFF\",\"textSize\":\"20sp\",\"gravity\":\"center\",\"textStyle\":\"bold\",\"paddingStart\":\"16dp\",\"paddingEnd\":\"16dp\"}},"
                + "{\"type\":\"TextView\",\"props\":{\"id\":\"textView\",\"layout_width\":\"wrap_content\",\"layout_height\":\"wrap_content\",\"text\":\"" + escapeJson(text) + "\",\"textColor\":\"#00FF00\",\"textSize\":\"16sp\",\"gravity\":\"center\",\"marginTop\":\"16dp\",\"paddingStart\":\"16dp\",\"paddingEnd\":\"16dp\"}}"
                + "]"
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
