---
name: add-api-endpoint
description: >-
  Walks through implementing a complete vertical API slice across //core contracts,
  server-db, server-public or server-admin, Ktor testHost tests, Bruno requests, and
  client FlowMVI stores. Use when adding or modifying Portfolio HTTP endpoints.
---
# Add or Modify an API Endpoint (Full Vertical Slice)

Implement endpoints end-to-end across contracts, backend, tests, Bruno, and clients so no half-wired stubs remain.

## 1. Shared Contract (`//core`)

- Add immutable `@Serializable` `data class` DTOs (`val` only, read-only `List`/`Map`/`Set`) in `core/src/codes/cy/portfolio/core/model/`.
- Define the type-safe Ktor `@Resource` route in `core/src/codes/cy/portfolio/core/routes/ApiRoutes.kt`.
- Reuse `QueryOptions` / `PagedResult<T>` for paginated lists and `ApiError` for all error responses.

## 2. Persistence (`server/server-db`)

- Add query/command methods to the appropriate `*QueryRepository` / `*CommandRepository` interface and `Exposed*Repository` implementation in `PortfolioRepositories.kt` using Exposed 1.0 DSL inside `dbFactory.dbQuery`.
- Preserve `portfolio_public` read-only / `portfolio_admin` DML role boundaries.

## 3. Server Layer (`server/server-public` or `server/server-admin`)

- **Domain validation:** Add pure validation rules throwing `ValidationException` (e.g., `AdminValidation.kt`). Never duplicate inline validation in routes.
- **Application service:** Implement inside `*QueryService` or `*CommandService` following strict CQS (`GET` performs zero DB writes; mutations generate server-side UUIDs and `Clock` timestamps).
- **API route:** Register the `@Resource` handler in `PublicApiRoutes.kt` or `AdminApiRoutes.kt`:
  - `POST` -> `201 Created` + `Location` header (`call.respondCreated(...)`)
  - `PUT` -> `200 OK` (or `404 Not Found`)
  - `DELETE` -> `204 No Content` (or `404 Not Found`)

## 4. Contract Tests & Bruno Suite

- Add `testHost` tests in `PublicServerContractTest.kt` or `AdminServerContractTest.kt` covering **both** happy paths and negative cases (`400`, `401`/`403`, `404`, `409`). Implement real in-memory fake methods in the test harness—never leave `error("unused")` stubs for live endpoints.
- Add matching `.bru` requests under `bruno/01-Public-Portfolio`, `02-Public-Writeups-And-SEO`, `03-Admin-CMS`, `04-Admin-Documents-And-Stats`, and `05-Negative-Tests` using `{{publicBaseUrl}}`, `{{adminBaseUrl}}`, and `{{adminToken}}`.

## 5. Client Integration (`app/presentation` or `app/adminShared`)

- Wire Ktor Client calls (`PortfolioApiClient.kt` or `AdminApiClient.kt`), handle `ApiError` exhaustively, update FlowMVI stores/contracts, and add store unit tests (`PortfolioStoresTest.kt` or `AdminStoresTest.kt`).
- If public visitor UI is affected, also check `app/composeUi` and `app/iosApp` SwiftUI screens.

## 6. Verify

1. Run `./scripts/verify.sh --fast` (compiles contracts/clients/servers, runs all JVM tests + Konsist).
2. If Docker is running, run `./scripts/up.sh local --seed && ./scripts/smoke.sh local --bruno`.
