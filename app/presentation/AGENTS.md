# Pure KMP Presentation Rules (`app/presentation`)

- **Zero Compose imports:** Never import `androidx.compose.*`, `org.jetbrains.compose.*`, `io.ktor.server.*`, or `org.jetbrains.exposed.*` across `src/` or any `src@*` platform source set (enforced by Konsist `ArchitectureFitnessTest`).
- **Decompose navigation:** Components own `ComponentContext`, `ChildStack`, and `ChildPanels`. Navigation is strictly parent-driven via sealed child `Output` events; sync web URLs via `WebHistoryController` on `wasmJs`.
- **FlowMVI stores:** Keep all `State`, `Intent`, and `Action` types immutable (`data class`/`sealed interface` with `val` properties and read-only `List`/`Map`/`Set`). Install cross-cutting logging, analytics, and error handling as FlowMVI plugins.
- **Shared contracts:** Call `//core` Ktor `@Resource` endpoints with `PortfolioJson` and switch exhaustively over `ApiError` via `when (error)`.
- **Verify:** `./scripts/verify.sh --fast --module presentation`
