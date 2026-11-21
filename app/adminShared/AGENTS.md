# Admin Compose UI Rules (`app/adminShared`)

- **Bundle isolation:** Keep all admin screens, stores, and API clients inside `app/adminShared`, `app/adminDesktopApp`, and `app/adminWebApp`. Public client modules (`app/presentation`, `app/composeUi`, `app/webApp`, `app/androidApp`, `app/desktopApp`, `app/iosApp`) must never import `codes.cy.portfolio.admin.*`.
- **Reuse shared UI & contracts:** Consume `//core` `@Resource` contracts / `PortfolioJson` / `ApiError` and reuse `//app/composeUi` Markdown + `DemoRegistry` for pixel-accurate writeup live preview.
- **Direct-to-R2 uploads:** Follow the 3-step presigned upload lifecycle (client SHA-256 -> `POST /documents/presign` -> direct `PUT` to presigned URL -> `POST /documents/confirm`).
- **Isolation:** Never import `io.ktor.server.*` or `org.jetbrains.exposed.*`.
- **Verify:** `./scripts/verify.sh --fast --module adminShared`
