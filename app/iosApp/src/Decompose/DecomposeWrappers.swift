import Combine
import SwiftUI
import UIKit
import KotlinModules

/// Observes a Decompose `Value<T>` on the main queue and automatically cancels on deinit.
final class ObservableValue<T: AnyObject>: ObservableObject {
    @Published var value: T
    private var cancellation: DecomposeCancellation?

    init(_ decomposeValue: DecomposeValue<T>) {
        self.value = decomposeValue.value
        self.cancellation = decomposeValue.subscribe { [weak self] nextValue in
            guard let self else { return }
            if Thread.isMainThread {
                self.value = nextValue
            } else {
                DispatchQueue.main.async { [weak self] in
                    self?.value = nextValue
                }
            }
        }
    }

    deinit {
        cancellation?.cancel()
    }
}

/// Property wrapper mirroring Compose's `subscribeAsState()` for Decompose `Value<T>`.
@propertyWrapper
struct StateValue<T: AnyObject>: DynamicProperty {
    @ObservedObject private var observable: ObservableValue<T>

    init(_ decomposeValue: DecomposeValue<T>) {
        self._observable = ObservedObject(wrappedValue: ObservableValue(decomposeValue))
    }

    var wrappedValue: T {
        observable.value
    }
}

/// Renders the currently active child inside a Decompose `Value<ChildStack<C, T>>`.
struct StackView<C: AnyObject, T: AnyObject, Content: View>: View {
    @StateValue var stack: DecomposeChildStack<C, T>
    private let content: (T) -> Content

    init(
        stack: DecomposeValue<DecomposeChildStack<C, T>>,
        @ViewBuilder content: @escaping (T) -> Content
    ) {
        self._stack = StateValue(stack)
        self.content = content
    }

    var body: some View {
        content(stack.active.instance)
    }
}

/// Retains the Kotlin/Decompose `IosPortfolioAppHost` and bridges side-effect links/downloads to iOS.
final class PortfolioAppModel: ObservableObject {
    private static let envApiBaseUrlKey = "PORTFOLIO_API_BASE_URL"
    private static let fallbackApiBaseUrl = "http://127.0.0.1:8080"

    let host: IosPortfolioAppHost

    init() {
        let baseUrl = Self.resolveApiBaseUrl()
        self.host = IosPortfolioAppHost(
            apiBaseUrl: baseUrl,
            initialPath: nil,
            onExternalUrl: { rawUrl in
                Self.openInSystemBrowser(rawUrl)
            },
            onDownload: { rawUrl, _ in
                Self.openInSystemBrowser(rawUrl)
            }
        )
    }

    deinit {
        host.onDestroy()
    }

    private static func resolveApiBaseUrl() -> String {
        if let envUrl = ProcessInfo.processInfo.environment[envApiBaseUrlKey]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !envUrl.isEmpty {
            return envUrl
        }
        if let plistUrl = (Bundle.main.object(forInfoDictionaryKey: envApiBaseUrlKey) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !plistUrl.isEmpty {
            return plistUrl
        }
        return fallbackApiBaseUrl
    }

    private static func openInSystemBrowser(_ rawUrl: String) {
        guard let parsed = URL(string: rawUrl) else { return }
        DispatchQueue.main.async {
            UIApplication.shared.open(parsed)
        }
    }
}
