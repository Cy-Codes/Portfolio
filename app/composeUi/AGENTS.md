# Public Compose UI Rules (`app/composeUi`)

- **Adaptive `dp` breakpoints:** Drive layouts exclusively from `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` (`Compact` `<600dp`, `Medium` `600–839dp`, `Expanded` `840–1199dp`, `Large` `1200–1599dp`, `Extra-Large` `>=1600dp`). Never switch layout structure by OS or physical device checks; layer input modality (hover, shortcuts, scrollbars) separately.
- **Markdown & demos:** Render writeups with `multiplatform-markdown-renderer` + `Highlights`. Resolve fenced `demo:<id>` blocks strictly via `DemoRegistry` (allowlisted IDs only, with an explicit fallback card for unknown IDs).
- **Isolation:** Never import `codes.cy.portfolio.admin.*`, `io.ktor.server.*`, or `org.jetbrains.exposed.*` across any KMP source set.
- **Verify:** `./scripts/verify.sh --fast --module composeUi`
