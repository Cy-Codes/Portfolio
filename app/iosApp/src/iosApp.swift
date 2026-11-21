import Combine
import SwiftUI
import KotlinModules

@main
struct iosApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var appModel = PortfolioAppModel()

    var body: some Scene {
        WindowGroup {
            RootView(rootComponent: appModel.host.rootComponent)
                .onOpenURL { incomingUrl in
                    appModel.host.handleDeepLink(rawUrl: incomingUrl.absoluteString)
                }
        }
        .onChange(of: scenePhase, initial: true) { _, nextPhase in
            switch nextPhase {
            case .active:
                appModel.host.onActive()
            case .background:
                appModel.host.onBackground()
            default:
                break
            }
        }
    }
}
