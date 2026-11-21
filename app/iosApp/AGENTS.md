# Native SwiftUI iOS Rules (`app/iosApp`)

- **Native visitor UI + Compose islands:** Build visitor screens in native SwiftUI (`TabView` for compact, `NavigationSplitView` for regular) bound directly to `//app/presentation` Decompose state via `@StateValue` (`DecomposeWrappers.swift`) and `IosPortfolioBridge.kt`.
- **Embedded demos only:** Render prose and syntax-highlighted code natively in SwiftUI; embed Compose inline *only* for `demo:<id>` interactive blocks via `ComposeUIViewController` + `UIViewControllerRepresentable` (`ComposeDemoIslandView.swift`).
- **Isolation:** Keep `iosApp` strictly visitor-facing—zero admin UI or `codes.cy.portfolio.admin.*` imports.
- **Verify:** `./kotlin test -m iosApp` and `./kotlin build` *(requires unsandboxed host access for Xcode)*.
