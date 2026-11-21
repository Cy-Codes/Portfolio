import SwiftUI
import KotlinModules

struct RootView: View {
    private static let expandedMinWidthPt: CGFloat = 840
    private static let mediumMinWidthPt: CGFloat = 600

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let rootComponent: RootComponent

    @StateValue private var activeDestination: TopLevelDestination
    @StateValue private var childStack: DecomposeChildStack<RootConfig, RootComponentChild>

    init(rootComponent: RootComponent) {
        self.rootComponent = rootComponent
        self._activeDestination = StateValue(rootComponent.activeDestination)
        self._childStack = StateValue(rootComponent.childStack)
    }

    var body: some View {
        GeometryReader { proxy in
            Group {
                if horizontalSizeClass == .compact {
                    compactTabShell
                } else {
                    regularSidebarShell
                }
            }
            .onAppear {
                updateViewportTier(widthPt: proxy.size.width)
            }
            .onChange(of: proxy.size.width) { _, newWidth in
                updateViewportTier(widthPt: newWidth)
            }
        }
    }

    private var compactTabShell: some View {
        TabView(selection: destinationBinding) {
            ForEach(TopLevelDestination.entries, id: \.self) { destination in
                renderActiveChildIfMatches(destination)
                    .tabItem {
                        Label(destination.tabTitle, systemImage: destination.systemImageName)
                    }
                    .tag(destination)
            }
        }
    }

    private var regularSidebarShell: some View {
        NavigationSplitView {
            List(TopLevelDestination.entries, id: \.self, selection: optionalDestinationBinding) { destination in
                Label(destination.sidebarTitle, systemImage: destination.systemImageName)
                    .tag(destination)
            }
            .navigationTitle("CY · Portfolio")
        } detail: {
            renderChild(childStack.active.instance)
        }
    }

    private var destinationBinding: Binding<TopLevelDestination> {
        Binding(
            get: { activeDestination },
            set: { next in
                rootComponent.selectDestination(destination: next)
            }
        )
    }

    private var optionalDestinationBinding: Binding<TopLevelDestination?> {
        Binding(
            get: { activeDestination },
            set: { next in
                if let destination = next {
                    rootComponent.selectDestination(destination: destination)
                }
            }
        )
    }

    @ViewBuilder
    private func renderActiveChildIfMatches(_ destination: TopLevelDestination) -> some View {
        if activeDestination == destination {
            renderChild(childStack.active.instance)
        } else {
            Color(.systemGroupedBackground)
        }
    }

    @ViewBuilder
    private func renderChild(_ instance: RootComponentChild) -> some View {
        if let profile = instance as? RootComponentChildProfile {
            ProfileScreenView(component: profile.component)
        } else if let projects = instance as? RootComponentChildProjects {
            ProjectsScreenView(component: projects.component)
        } else if let writeups = instance as? RootComponentChildWriteups {
            WriteupsScreenView(component: writeups.component)
        } else if let docs = instance as? RootComponentChildResumeDocs {
            ResumeDocsScreenView(component: docs.component)
        } else {
            PortfolioLoadingView(message: "Initializing...")
        }
    }

    private func updateViewportTier(widthPt: CGFloat) {
        let tier: ViewportTier
        if widthPt >= Self.expandedMinWidthPt {
            tier = .expanded
        } else if widthPt >= Self.mediumMinWidthPt {
            tier = .medium
        } else {
            tier = .compact
        }
        rootComponent.updateViewportTier(tier: tier)
    }
}

private extension TopLevelDestination {
    var tabTitle: String {
        switch self {
        case .profile: return "Profile"
        case .projects: return "Projects"
        case .writeups: return "Writeups"
        case .resumeDocs: return "Docs"
        default: return "Portfolio"
        }
    }

    var sidebarTitle: String {
        switch self {
        case .profile: return "Profile & Career"
        case .projects: return "Projects"
        case .writeups: return "Engineering Writeups"
        case .resumeDocs: return "Resume & Docs"
        default: return "Portfolio"
        }
    }

    var systemImageName: String {
        switch self {
        case .profile: return "person.crop.circle"
        case .projects: return "square.grid.2x2"
        case .writeups: return "doc.richtext"
        case .resumeDocs: return "folder"
        default: return "sparkles"
        }
    }
}
