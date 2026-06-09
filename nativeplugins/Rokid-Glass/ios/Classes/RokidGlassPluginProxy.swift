import Foundation
import UIKit

@objc(RokidGlassPluginProxy)
@objcMembers
public class RokidGlassPluginProxy: NSObject {
    public func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        return true
    }

    public func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return RokidGlassBridge.handleOpenURL(url)
    }

    public func application(_ application: UIApplication, open url: URL, sourceApplication: String?, annotation: Any) -> Bool {
        return RokidGlassBridge.handleOpenURL(url)
    }

    public func application(_ application: UIApplication, handleOpen url: URL) -> Bool {
        return RokidGlassBridge.handleOpenURL(url)
    }

    public func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([Any]?) -> Void) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb, let url = userActivity.webpageURL else {
            return false
        }
        return RokidGlassBridge.handleOpenURL(url)
    }
}

@objc(RokidCXRLPluginProxy)
@objcMembers
public final class RokidCXRLPluginProxy: RokidGlassPluginProxy {}
