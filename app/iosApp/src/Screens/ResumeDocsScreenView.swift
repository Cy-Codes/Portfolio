import SwiftUI
import KotlinModules

struct ResumeDocsScreenView: View {
    let component: ResumeDocsComponent
    @StateValue private var state: ResumeDocsState

    init(component: ResumeDocsComponent) {
        self.component = component
        self._state = StateValue(component.state)
    }

    var body: some View {
        NavigationStack {
            Group {
                if state is ResumeDocsStateLoading {
                    PortfolioLoadingView(message: "Loading resumes & documents...")
                } else if let error = state as? ResumeDocsStateError {
                    PortfolioErrorView(message: error.displayMessage) {
                        IosPortfolioBridge.shared.dispatchResumeDocs(
                            component: component,
                            intent: ResumeDocsIntentLoad.shared
                        )
                    }
                } else if let content = state as? ResumeDocsStateContent {
                    documentsListView(content)
                }
            }
            .navigationTitle("Resume & Docs")
        }
    }

    @ViewBuilder
    private func documentsListView(_ content: ResumeDocsStateContent) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                if !content.availableCategories.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button("All") {
                                IosPortfolioBridge.shared.dispatchResumeDocs(
                                    component: component,
                                    intent: ResumeDocsIntentSelectCategory(category: nil)
                                )
                            }
                            .buttonStyle(.bordered)
                            .tint(content.selectedCategory == nil ? .accentColor : .secondary)

                            ForEach(content.availableCategories, id: \.name) { category in
                                Button(IosPortfolioBridge.shared.documentCategoryLabel(category: category)) {
                                    IosPortfolioBridge.shared.dispatchResumeDocs(
                                        component: component,
                                        intent: ResumeDocsIntentSelectCategory(category: category)
                                    )
                                }
                                .buttonStyle(.bordered)
                                .tint(content.selectedCategory == category ? .accentColor : .secondary)
                            }
                        }
                    }
                }

                if content.filteredDocuments.isEmpty {
                    ContentUnavailableView(
                        "No Public Documents",
                        systemImage: "doc",
                        description: Text("Select another category to view published resumes and slides.")
                    )
                } else {
                    ForEach(content.filteredDocuments, id: \.id) { doc in
                        PortfolioCardContainer {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(doc.title).font(.headline)
                                    Text(doc.originalFilename)
                                        .font(.subheadline)
                                        .foregroundStyle(.tint)
                                }
                                Spacer()
                                MonoBadgeView(
                                    text: IosPortfolioBridge.shared.documentCategoryLabel(category: doc.category),
                                    foreground: .accentColor
                                )
                            }

                            FlowChipsLayout {
                                MonoBadgeView(text: IosPortfolioBridge.shared.formatByteSize(bytes: doc.sizeBytes))
                                MonoBadgeView(text: doc.mimeType)
                                MonoBadgeView(text: "SHA256 \(String(doc.sha256.prefix(12)))")
                            }

                            Button {
                                IosPortfolioBridge.shared.dispatchResumeDocs(
                                    component: component,
                                    intent: ResumeDocsIntentRequestDownload(document: doc)
                                )
                            } label: {
                                Label("Download", systemImage: "arrow.down.circle.fill")
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                }
            }
            .padding()
            .frame(maxWidth: 920)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .refreshable {
            IosPortfolioBridge.shared.dispatchResumeDocs(
                component: component,
                intent: ResumeDocsIntentRefresh.shared
            )
        }
    }
}
