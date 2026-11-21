import SwiftUI
import KotlinModules

struct ProfileScreenView: View {
    let component: ProfileComponent
    @StateValue private var state: ProfileState

    init(component: ProfileComponent) {
        self.component = component
        self._state = StateValue(component.state)
    }

    var body: some View {
        NavigationStack {
            Group {
                if state is ProfileStateLoading {
                    PortfolioLoadingView(message: "Loading profile...")
                } else if let error = state as? ProfileStateError {
                    PortfolioErrorView(message: error.displayMessage) {
                        IosPortfolioBridge.shared.dispatchProfile(
                            component: component,
                            intent: ProfileIntentRefresh.shared
                        )
                    }
                } else if let content = state as? ProfileStateContent {
                    profileContentView(content)
                }
            }
            .navigationTitle("Profile")
        }
    }

    @ViewBuilder
    private func profileContentView(_ content: ProfileStateContent) -> some View {
        let overview = content.overview
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                heroCard(overview: overview, isRefreshing: content.isRefreshing)
                skillsSection(content: content)
                experienceSection(experiences: overview.experiences)
                if !overview.education.isEmpty {
                    educationSection(education: overview.education)
                }
                if !overview.certifications.isEmpty {
                    certificationsSection(certifications: overview.certifications)
                }
            }
            .padding()
            .frame(maxWidth: 920)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .refreshable {
            IosPortfolioBridge.shared.dispatchProfile(
                component: component,
                intent: ProfileIntentRefresh.shared
            )
        }
    }

    @ViewBuilder
    private func heroCard(overview: PortfolioOverviewDto, isRefreshing: Bool) -> some View {
        let profile = overview.profile
        PortfolioCardContainer {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(profile?.fullName ?? "Engineering Portfolio")
                        .font(.title.weight(.bold))
                    Text(profile?.headline ?? "Kotlin Multiplatform & Backend Systems")
                        .font(.headline)
                        .foregroundStyle(.tint)
                }
                Spacer()
                if isRefreshing {
                    ProgressView()
                }
            }

            FlowChipsLayout {
                if let location = profile?.location, !location.isEmpty {
                    MonoBadgeView(text: location)
                }
                if let availability = profile?.availability, !availability.isEmpty {
                    MonoBadgeView(text: availability, foreground: .accentColor)
                }
            }

            if let bio = profile?.bioMarkdown, !bio.isEmpty {
                NativeMarkdownView(markdown: bio)
            }

            FlowChipsLayout {
                Button("Projects") {
                    IosPortfolioBridge.shared.dispatchProfile(
                        component: component,
                        intent: ProfileIntentOpenProjects.shared
                    )
                }
                .buttonStyle(.borderedProminent)

                Button("Writeups") {
                    IosPortfolioBridge.shared.dispatchProfile(
                        component: component,
                        intent: ProfileIntentOpenWriteups.shared
                    )
                }
                .buttonStyle(.bordered)

                Button("Resume & Docs") {
                    IosPortfolioBridge.shared.dispatchProfile(
                        component: component,
                        intent: ProfileIntentOpenResumeDocs.shared
                    )
                }
                .buttonStyle(.bordered)

                ForEach(profile?.socialLinks ?? [], id: \.label) { social in
                    Button {
                        IosPortfolioBridge.shared.dispatchProfile(
                            component: component,
                            intent: ProfileIntentClickSocialLink(url: social.url)
                        )
                    } label: {
                        Label(social.label, systemImage: "arrow.up.right.square")
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    @ViewBuilder
    private func skillsSection(content: ProfileStateContent) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Core Stack")
                .font(.title3.weight(.semibold))

            if content.overview.featuredSkills.isEmpty {
                Text("No featured skills published yet.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button("All") {
                            IosPortfolioBridge.shared.dispatchProfile(
                                component: component,
                                intent: ProfileIntentSelectSkillCategory(category: nil)
                            )
                        }
                        .buttonStyle(.bordered)
                        .tint(content.selectedSkillCategory == nil ? .accentColor : .secondary)

                        ForEach(content.skillCategories, id: \.self) { category in
                            Button(category) {
                                IosPortfolioBridge.shared.dispatchProfile(
                                    component: component,
                                    intent: ProfileIntentSelectSkillCategory(category: category)
                                )
                            }
                            .buttonStyle(.bordered)
                            .tint(content.selectedSkillCategory == category ? .accentColor : .secondary)
                        }
                    }
                }

                FlowChipsLayout {
                    ForEach(content.filteredSkills, id: \.id) { skill in
                        MonoBadgeView(text: "\(skill.name) · \(skill.category)", foreground: .primary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func experienceSection(experiences: [ExperienceDto]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Experience")
                .font(.title3.weight(.semibold))

            ForEach(experiences, id: \.id) { exp in
                PortfolioCardContainer {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(exp.title)
                                .font(.headline)
                            Text([exp.company, exp.location].compactMap { $0 }.joined(separator: " · "))
                                .font(.subheadline)
                                .foregroundStyle(.tint)
                        }
                        Spacer()
                        MonoBadgeView(text: experienceRange(exp))
                    }

                    ForEach(exp.highlights, id: \.self) { bullet in
                        Text("• \(bullet)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    if !exp.skills.isEmpty {
                        FlowChipsLayout {
                            ForEach(exp.skills, id: \.id) { skill in
                                MonoBadgeView(text: skill.name)
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func educationSection(education: [EducationDto]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Education")
                .font(.title3.weight(.semibold))
            ForEach(education, id: \.id) { edu in
                let startLabel = IosPortfolioBridge.shared.formatLocalDate(date: edu.startDate)
                let endLabel = edu.endDate.map { IosPortfolioBridge.shared.formatLocalDate(date: $0) } ?? "Present"
                PortfolioCardContainer {
                    Text(edu.degree).font(.headline)
                    Text([edu.institution, edu.fieldOfStudy].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    MonoBadgeView(text: "\(startLabel) — \(endLabel)")
                }
            }
        }
    }

    @ViewBuilder
    private func certificationsSection(certifications: [CertificationDto]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Certifications")
                .font(.title3.weight(.semibold))
            ForEach(certifications, id: \.id) { cert in
                let issued = IosPortfolioBridge.shared.formatLocalDate(date: cert.issuedDate)
                PortfolioCardContainer {
                    Text(cert.name).font(.headline)
                    Text(cert.issuer).font(.subheadline).foregroundStyle(.secondary)
                    HStack {
                        MonoBadgeView(text: "Issued \(issued)")
                        if let url = cert.credentialUrl {
                            Button {
                                IosPortfolioBridge.shared.dispatchProfile(
                                    component: component,
                                    intent: ProfileIntentClickSocialLink(url: url)
                                )
                            } label: {
                                Label("Verify", systemImage: "arrow.up.right.square")
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
            }
        }
    }

    private func experienceRange(_ exp: ExperienceDto) -> String {
        let start = IosPortfolioBridge.shared.formatLocalDate(date: exp.startDate)
        let end = exp.isCurrent
            ? "Present"
            : (exp.endDate.map { IosPortfolioBridge.shared.formatLocalDate(date: $0) } ?? "Present")
        return "\(start) — \(end)"
    }
}
