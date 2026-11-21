import SwiftUI
import KotlinModules

struct WriteupsScreenView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let component: WriteupsPanelsComponent

    @StateValue private var panels: DecomposeChildPanels<
        WriteupsPanelsComponentWriteupsListConfig,
        WriteupsPanelsComponentWriteupsListChild,
        WriteupsPanelsComponentReadArticleConfig,
        WriteupsPanelsComponentReadArticleComponent,
        WriteupsPanelsComponentArticleExtraConfig,
        WriteupsPanelsComponentArticleExtraChild
    >

    init(component: WriteupsPanelsComponent) {
        self.component = component
        self._panels = StateValue(component.panels)
    }

    var body: some View {
        let listChild = panels.main.instance
        let readerChild = panels.details?.instance
        let extraChild = panels.extra?.instance

        Group {
            if horizontalSizeClass == .compact {
                NavigationStack {
                    WriteupsListPaneView(
                        listChild: listChild,
                        selectedSlug: panels.details?.configuration.slug,
                        isCompact: true
                    )
                    .navigationTitle("Writeups")
                    .navigationDestination(item: selectedSlugBinding) { _ in
                        if let reader = readerChild {
                            ReadArticleDetailView(
                                component: reader,
                                extraChild: extraChild
                            )
                        }
                    }
                }
            } else {
                NavigationSplitView {
                    WriteupsListPaneView(
                        listChild: listChild,
                        selectedSlug: panels.details?.configuration.slug,
                        isCompact: false
                    )
                    .navigationTitle("Writeups")
                } detail: {
                    if let reader = readerChild {
                        ReadArticleDetailView(
                            component: reader,
                            extraChild: extraChild
                        )
                    } else {
                        ContentUnavailableView(
                            "Select a Writeup",
                            systemImage: "doc.richtext",
                            description: Text("Read native prose, syntax-highlighted code, and interactive Compose demo islands.")
                        )
                    }
                }
            }
        }
        .onAppear { syncPanelMode() }
        .onChange(of: horizontalSizeClass) { _, _ in syncPanelMode() }
    }

    private var selectedSlugBinding: Binding<String?> {
        Binding(
            get: { panels.details?.configuration.slug },
            set: { nextSlug in
                if nextSlug == nil, panels.details != nil {
                    component.closeWriteup()
                }
            }
        )
    }

    private func syncPanelMode() {
        let targetMode: DecomposeChildPanelsMode = (horizontalSizeClass == .compact) ? .single : .dual
        component.setMode(mode: targetMode)
    }
}

private struct WriteupsListPaneView: View {
    let listChild: WriteupsPanelsComponentWriteupsListChild
    let selectedSlug: String?
    let isCompact: Bool

    @StateValue private var state: WriteupsState

    init(
        listChild: WriteupsPanelsComponentWriteupsListChild,
        selectedSlug: String?,
        isCompact: Bool
    ) {
        self.listChild = listChild
        self.selectedSlug = selectedSlug
        self.isCompact = isCompact
        self._state = StateValue(listChild.state)
    }

    var body: some View {
        List {
            if !state.availableTags.isEmpty || state.activeTag != nil {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button("All Tags") {
                                IosPortfolioBridge.shared.dispatchWriteups(
                                    child: listChild,
                                    intent: WriteupsIntentSelectTag(tag: nil)
                                )
                            }
                            .buttonStyle(.bordered)
                            .tint(state.activeTag == nil ? .accentColor : .secondary)

                            ForEach(state.availableTags, id: \.self) { tag in
                                Button(tag) {
                                    let next = (state.activeTag == tag) ? nil : tag
                                    IosPortfolioBridge.shared.dispatchWriteups(
                                        child: listChild,
                                        intent: WriteupsIntentSelectTag(tag: next)
                                    )
                                }
                                .buttonStyle(.bordered)
                                .tint(state.activeTag == tag ? .accentColor : .secondary)
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                }
            }

            if let errorMessage = state.displayErrorMessage, state.items.isEmpty {
                PortfolioErrorView(message: errorMessage) {
                    IosPortfolioBridge.shared.dispatchWriteups(
                        child: listChild,
                        intent: WriteupsIntentLoadInitial.shared
                    )
                }
            } else if state.isLoading && state.items.isEmpty {
                PortfolioLoadingView(message: "Loading writeups...")
            } else if state.items.isEmpty {
                ContentUnavailableView(
                    "No Matching Writeups",
                    systemImage: "magnifyingglass",
                    description: Text("Clear search or tag filters to browse published notes.")
                )
            } else {
                Section {
                    ForEach(state.items, id: \.id) { writeup in
                        Button {
                            IosPortfolioBridge.shared.dispatchWriteups(
                                child: listChild,
                                intent: WriteupsIntentSelectWriteup(slug: writeup.slug)
                            )
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(writeup.title)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                if let subtitle = writeup.subtitle, !subtitle.isEmpty {
                                    Text(subtitle)
                                        .font(.subheadline)
                                        .foregroundStyle(.tint)
                                }
                                Text(writeup.summary)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                                HStack(spacing: 6) {
                                    MonoBadgeView(text: "\(writeup.readingTimeMin) min read")
                                    if let published = writeup.publishedAt {
                                        MonoBadgeView(
                                            text: IosPortfolioBridge.shared.formatInstant(instant: published)
                                        )
                                    }
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(
                            writeup.slug == selectedSlug && !isCompact
                                ? Color.accentColor.opacity(0.12)
                                : nil
                        )
                    }

                    if state.hasNextPage {
                        Button("Load More Writeups") {
                            IosPortfolioBridge.shared.dispatchWriteups(
                                child: listChild,
                                intent: WriteupsIntentLoadNextPage.shared
                            )
                        }
                        .disabled(state.isLoading)
                    }
                }
            }
        }
        .searchable(
            text: Binding(
                get: { state.searchQuery },
                set: { nextQuery in
                    IosPortfolioBridge.shared.dispatchWriteups(
                        child: listChild,
                        intent: WriteupsIntentUpdateSearchQuery(query: nextQuery)
                    )
                }
            ),
            prompt: "Search technical writeups"
        )
        .onSubmit(of: .search) {
            IosPortfolioBridge.shared.dispatchWriteups(
                child: listChild,
                intent: WriteupsIntentSubmitSearch.shared
            )
        }
        .refreshable {
            IosPortfolioBridge.shared.dispatchWriteups(
                child: listChild,
                intent: WriteupsIntentRefresh.shared
            )
        }
    }
}

private struct ReadArticleDetailView: View {
    let component: WriteupsPanelsComponentReadArticleComponent
    let extraChild: WriteupsPanelsComponentArticleExtraChild?

    @StateValue private var state: ReadArticleState
    @State private var showingOutlineSheet = false

    init(
        component: WriteupsPanelsComponentReadArticleComponent,
        extraChild: WriteupsPanelsComponentArticleExtraChild?
    ) {
        self.component = component
        self.extraChild = extraChild
        self._state = StateValue(component.state)
    }

    var body: some View {
        Group {
            if let loading = state as? ReadArticleStateLoading {
                PortfolioLoadingView(message: "Loading '\(loading.slug)'...")
            } else if let error = state as? ReadArticleStateError {
                PortfolioErrorView(message: error.displayMessage) {
                    IosPortfolioBridge.shared.dispatchReadArticle(
                        component: component,
                        intent: ReadArticleIntentRetry.shared
                    )
                }
            } else if let content = state as? ReadArticleStateContent {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(content.writeup.title)
                            .font(.title.weight(.bold))

                        if let subtitle = content.writeup.subtitle, !subtitle.isEmpty {
                            Text(subtitle)
                                .font(.title3)
                                .foregroundStyle(.secondary)
                        }

                        FlowChipsLayout {
                            MonoBadgeView(text: "\(content.writeup.readingTimeMin) min read")
                            if let published = content.writeup.publishedAt {
                                MonoBadgeView(
                                    text: "Published \(IosPortfolioBridge.shared.formatInstant(instant: published))"
                                )
                            }
                            ForEach(content.writeup.tags, id: \.self) { tag in
                                MonoBadgeView(text: tag)
                            }
                        }

                        Divider()

                        NativeMarkdownView(markdown: content.writeup.bodyMarkdown)
                    }
                    .padding()
                    .frame(maxWidth: 780)
                    .frame(maxWidth: .infinity)
                }
                .navigationTitle(content.writeup.title)
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if extraChild != nil {
                    Button {
                        showingOutlineSheet = true
                    } label: {
                        Label("Outline", systemImage: "list.bullet.indent")
                    }
                }
                Button {
                    IosPortfolioBridge.shared.dispatchReadArticle(
                        component: component,
                        intent: ReadArticleIntentOpenReaderHtmlVersion.shared
                    )
                } label: {
                    Label("HTML Reader", systemImage: "safari")
                }
            }
        }
        .sheet(isPresented: $showingOutlineSheet) {
            if let extra = extraChild {
                ArticleOutlineSheetView(extraChild: extra)
            }
        }
    }
}

private struct ArticleOutlineSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @StateValue private var headings: NSArray
    @StateValue private var demoIds: NSArray

    init(extraChild: WriteupsPanelsComponentArticleExtraChild) {
        self._headings = StateValue(extraChild.tocHeadings)
        self._demoIds = StateValue(extraChild.embeddedDemoIds)
    }

    var body: some View {
        let typedHeadings = (headings as? [TocHeading]) ?? []
        let typedDemos = (demoIds as? [String]) ?? []

        NavigationStack {
            List {
                Section("Table of Contents") {
                    if typedHeadings.isEmpty {
                        Text("No section headings found.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(typedHeadings, id: \.anchorId) { heading in
                            Text(heading.title)
                                .font(heading.level == 1 ? .headline : .body)
                                .padding(.leading, CGFloat(max(0, Int(heading.level) - 1) * 14))
                        }
                    }
                }

                if !typedDemos.isEmpty {
                    Section("Embedded Compose Demos") {
                        ForEach(typedDemos, id: \.self) { demoId in
                            Label("demo:\(demoId)", systemImage: "sparkles.rectangle.stack")
                                .font(.subheadline.monospaced())
                        }
                    }
                }
            }
            .navigationTitle("Article Outline")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
