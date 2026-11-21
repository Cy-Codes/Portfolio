import SwiftUI
import KotlinModules

struct ProjectsScreenView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let component: ProjectsPanelsComponent

    @StateValue private var state: ProjectsState
    @StateValue private var panels: DecomposeChildPanels<
        ProjectsPanelsComponentProjectsListConfig,
        ProjectsPanelsComponentProjectsListChild,
        ProjectsPanelsComponentProjectDetailConfig,
        ProjectsPanelsComponentProjectDetailChild,
        KotlinNothing,
        KotlinNothing
    >

    init(component: ProjectsPanelsComponent) {
        self.component = component
        self._state = StateValue(component.state)
        self._panels = StateValue(component.panels)
    }

    var body: some View {
        Group {
            if horizontalSizeClass == .compact {
                compactLayout
            } else {
                regularSplitLayout
            }
        }
        .onAppear { syncPanelMode() }
        .onChange(of: horizontalSizeClass) { _, _ in syncPanelMode() }
    }

    private var compactLayout: some View {
        NavigationStack {
            projectsListView
                .navigationTitle("Projects")
                .navigationDestination(item: selectedSlugBinding) { _ in
                    projectDetailView
                }
        }
    }

    private var regularSplitLayout: some View {
        NavigationSplitView {
            projectsListView
                .navigationTitle("Projects")
        } detail: {
            if panels.details != nil {
                projectDetailView
            } else {
                ContentUnavailableView(
                    "Select a Project",
                    systemImage: "square.grid.2x2",
                    description: Text("Choose a project on the left to read its full case study.")
                )
            }
        }
    }

    private var selectedSlugBinding: Binding<String?> {
        Binding(
            get: { panels.details?.configuration.slug },
            set: { nextSlug in
                if nextSlug == nil, panels.details != nil {
                    IosPortfolioBridge.shared.dispatchProjects(
                        component: component,
                        intent: ProjectsIntentClearSelection.shared
                    )
                }
            }
        )
    }

    private var projectsListView: some View {
        let availableSkills = Array(Set(state.items.flatMap { $0.skills.map(\.name) })).sorted()
        return List {
            Section {
                Toggle("Featured Only", isOn: Binding(
                    get: { state.featuredOnly },
                    set: { enabled in
                        IosPortfolioBridge.shared.dispatchProjects(
                            component: component,
                            intent: ProjectsIntentToggleFeaturedOnly(featuredOnly: enabled)
                        )
                    }
                ))

                if let selectedSkill = state.selectedSkill {
                    Button {
                        IosPortfolioBridge.shared.dispatchProjects(
                            component: component,
                            intent: ProjectsIntentFilterBySkill(skill: nil)
                        )
                    } label: {
                        Label("Skill: \(selectedSkill) (Clear)", systemImage: "xmark.circle")
                    }
                } else if !availableSkills.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(availableSkills, id: \.self) { skillName in
                                Button(skillName) {
                                    IosPortfolioBridge.shared.dispatchProjects(
                                        component: component,
                                        intent: ProjectsIntentFilterBySkill(skill: skillName)
                                    )
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                }
            }

            if let errorMessage = state.displayErrorMessage, state.items.isEmpty {
                PortfolioErrorView(message: errorMessage) {
                    IosPortfolioBridge.shared.dispatchProjects(
                        component: component,
                        intent: ProjectsIntentLoadInitial.shared
                    )
                }
            } else if state.isLoadingList && state.items.isEmpty {
                PortfolioLoadingView(message: "Loading projects...")
            } else if state.items.isEmpty {
                ContentUnavailableView(
                    "No Matching Projects",
                    systemImage: "folder",
                    description: Text("Clear active filters to browse all projects.")
                )
            } else {
                Section {
                    ForEach(state.items, id: \.id) { project in
                        Button {
                            IosPortfolioBridge.shared.dispatchProjects(
                                component: component,
                                intent: ProjectsIntentSelectProject(slug: project.slug)
                            )
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(project.title)
                                        .font(.headline)
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    MonoBadgeView(
                                        text: IosPortfolioBridge.shared.projectStatusLabel(status: project.status)
                                    )
                                }
                                Text(project.summary)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(
                            project.slug == state.selectedSlug && horizontalSizeClass != .compact
                                ? Color.accentColor.opacity(0.12)
                                : nil
                        )
                    }

                    if state.hasNextPage {
                        Button("Load More Projects") {
                            IosPortfolioBridge.shared.dispatchProjects(
                                component: component,
                                intent: ProjectsIntentLoadNextPage.shared
                            )
                        }
                        .disabled(state.isLoadingList)
                    }
                }
            }
        }
        .refreshable {
            IosPortfolioBridge.shared.dispatchProjects(
                component: component,
                intent: ProjectsIntentRefresh.shared
            )
        }
    }

    @ViewBuilder
    private var projectDetailView: some View {
        if state.isLoadingDetail && state.selectedProject == nil {
            PortfolioLoadingView(message: "Loading case study...")
        } else if let detail = state.selectedProject {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        MonoBadgeView(text: IosPortfolioBridge.shared.projectStatusLabel(status: detail.status))
                        MonoBadgeView(text: "Updated \(IosPortfolioBridge.shared.formatInstant(instant: detail.updatedAt))")
                        Spacer()
                    }

                    Text(detail.title)
                        .font(.title.weight(.bold))

                    Text(detail.summary)
                        .font(.body)
                        .foregroundStyle(.secondary)

                    if !detail.skills.isEmpty {
                        FlowChipsLayout {
                            ForEach(detail.skills, id: \.id) { skill in
                                MonoBadgeView(text: skill.name)
                            }
                        }
                    }

                    FlowChipsLayout {
                        if let liveUrl = detail.liveUrl, !liveUrl.isEmpty {
                            Button {
                                IosPortfolioBridge.shared.dispatchProjects(
                                    component: component,
                                    intent: ProjectsIntentOpenExternalLink(url: liveUrl)
                                )
                            } label: {
                                Label("Live Demo", systemImage: "safari")
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        if let repoUrl = detail.repoUrl, !repoUrl.isEmpty {
                            Button {
                                IosPortfolioBridge.shared.dispatchProjects(
                                    component: component,
                                    intent: ProjectsIntentOpenExternalLink(url: repoUrl)
                                )
                            } label: {
                                Label("Source Repo", systemImage: "chevron.left.forwardslash.chevron.right")
                            }
                            .buttonStyle(.bordered)
                        }
                    }

                    Divider()

                    NativeMarkdownView(markdown: detail.caseStudyMarkdown)
                }
                .padding()
                .frame(maxWidth: 780)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle(detail.title)
            .navigationBarTitleDisplayMode(.inline)
        } else {
            PortfolioErrorView(
                message: state.displayErrorMessage ?? "Select a project to view details."
            ) {
                if let slug = state.selectedSlug {
                    IosPortfolioBridge.shared.dispatchProjects(
                        component: component,
                        intent: ProjectsIntentSelectProject(slug: slug)
                    )
                }
            }
        }
    }

    private func syncPanelMode() {
        let targetMode: DecomposeChildPanelsMode = (horizontalSizeClass == .compact) ? .single : .dual
        component.setMode(mode: targetMode)
    }
}
