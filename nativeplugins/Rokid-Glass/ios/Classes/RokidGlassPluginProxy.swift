import Foundation
import UIKit

@objcMembers
public class RokidGlassPluginProxy: NSObject {
    public func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        return true
    }

    public func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return RokidGlassBridge.handleOpenURL(url)
    }

    public func application(_ application: UIApplication, handleOpen url: URL) -> Bool {
        return RokidGlassBridge.handleOpenURL(url)
    }
}

@objcMembers
public final class RokidCXRLPluginProxy: RokidGlassPluginProxy {}
