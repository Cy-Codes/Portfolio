# Portfolio — Architecture Decision Records (ADR) — v2

> **Revision Notice:** This document is **v2** of the Portfolio Architecture Decision Records, superseding the Cloudflare-centric edge, auth, and storage assumptions in [architecture-decisions.md](architecture-decisions.md) (v1, preserved for history).
>
> **Core Principle (v2): Open Standards & Clean Port Boundaries in App Code, Managed Commodities in the Cloud.**
> Production infrastructure standardizes on **Google Cloud Platform (GCP)** provisioned via **Terraform** and delivered via **CircleCI**, while keeping all application code coupled strictly to open protocols and hexagonal interfaces (**OCI containers**, **PostgreSQL 16**, **`BlobStorage` port**, **OIDC/JWKS**, **OpenTelemetry**, and **12-Factor environment variables**). Migrating to another cloud, Kubernetes, or a self-hosted Linux VM requires swapping only IaC and thin infrastructure adapters—not domain or UI code.

---

## Decision Matrix

| ADR | Area | Selected Approach | Status | Change vs. v1 |
| :--- | :--- | :--- | :--- | :--- |
| [ADR 1](architecture-decisions.md#adr-1-universal-coding-standards-functional-core-immutability-cqs-zero-magic-numbers) | Code Hygiene & Style | Functional-first, `val` immutability, CQS, zero magic numbers, extension helpers | Accepted | Unchanged |
| [ADR 2](architecture-decisions.md#adr-2-adaptive-ui-strategy-material-3-adaptive-dp-breakpoints-modality-and-custom-theme) | Public UI (Android, Desktop, `wasmJs`) | Material 3 Adaptive `dp` breakpoints + custom editorial theme on `wasmJs` | Accepted | Unchanged |
| [ADR 3](architecture-decisions.md#adr-3-native-swiftui-on-ios-with-embedded-compose-demo-islands) | iOS Client | Native SwiftUI (`TabView`, `NavigationSplitView`) + embedded `ComposeUIViewController` demo islands | Accepted | Unchanged |
| [ADR 4](architecture-decisions.md#adr-4-presentation-and-navigation-decompose-and-flowmvi-in-compose-free-apppresentation) | Shared Presentation & Routing | Pure KMP `app/presentation` (zero Compose imports) using Decompose + FlowMVI | Accepted | Unchanged |
| [ADR 5 (v2)](#adr-5-v2-compile-time-split-ktor-jvm-servers-and-generic-oidcjwks-admin-auth) | Backend Topology & Admin Auth | Compile-time split Ktor JVM servers + separate Admin UI; **generic OIDC/JWKS (Google OAuth)** replacing Cloudflare Access/Tunnel | Accepted | **Revised** |
| [ADR 6](architecture-decisions.md#adr-6-postgresql-16-exposed-10-dsl-only-one-shot-flyway-migrator-and-three-tier-roles) | Persistence & Schema Lifecycle | PostgreSQL 16 (`portfolio`, `analytics`), Exposed 1.0 DSL-only + HikariCP, one-shot Flyway migrator, 3 DB roles | Accepted | Unchanged *(hosted on Cloud SQL per ADR 11)* |
| [ADR 7](architecture-decisions.md#adr-7-pure-kmp-sealed-apierror-contract) | Client-Server Error Contract | Pure KMP `@Serializable sealed interface ApiError` in `//core` | Accepted | Unchanged |
| [ADR 8](architecture-decisions.md#adr-8-stateless-pagination-pure-get-downloads-and-deferred-concurrency-tokens) | API Semantics & Concurrency | Immutable `QueryOptions`/`PagedResult<T>` (CWE-190 guards), read-only `GET`, deferred `ETag`/`If-Match` | Accepted | Unchanged |
| [ADR 9 (v2)](#adr-9-v2-native-gcs-object-storage-behind-blobstorage-cookieless-analytics-and-ktor-readslug-seo) | Storage, Telemetry & Web SEO | **Native GCS (`GcsBlobStorage`)** with keyless IAM V4 signed URLs behind `BlobStorage`, cookieless events + rollups, Ktor `/read/{slug}` + dynamic Wasm SEO | Accepted | **Revised** |
| [ADR 10](architecture-decisions.md#adr-10-local-docker-emulation-testcontainers-konsist-fitness-functions-and-bruno-api-testing) | Containers & Verification | Profiled `compose.yaml`, Testcontainers, Konsist boundary assertions, Bruno API suite | Accepted | Unchanged |
| [ADR 11](#adr-11-gcp-runtime-architecture-cloud-run-cloud-run-jobs-cloud-sql-and-managed-https) | Cloud Runtime & Database | **GCP Cloud Run** (`server-public`, `server-admin`), **Cloud Run Jobs** (`db-migrator`), **Cloud SQL for PostgreSQL 16**, Managed HTTPS | Accepted | **New** |
| [ADR 12](#adr-12-infrastructure-as-code-with-terraform-and-gcp-secret-manager) | IaC & Secrets Management | **Terraform** (`hashicorp/google`, GCS remote state) + **GCP Secret Manager** injected as standard env vars | Accepted | **New** |
| [ADR 13](#adr-13-vendor-neutral-observability-via-opentelemetry-gcp-cloud-loggingmonitoring-first-oss-ready) | Observability | **OpenTelemetry** + structured JSON logs → **GCP Cloud Logging & Monitoring** first, with OTLP/Prometheus portability for OSS (Grafana/Prometheus/Loki) | Accepted | **New** |
| [ADR 14](#adr-14-unified-multi-platform-cicd-with-circleci-and-keyless-gcp-oidc) | CI/CD | **CircleCI** across Linux, Android, and path-filtered macOS/iOS, calling repo-owned scripts + keyless GCP Workload Identity Federation | Accepted | **New** |

---

## Revised Decisions (Superseding v1)

### ADR 5 (v2): Compile-Time Split Ktor JVM Servers and Generic OIDC/JWKS Admin Auth

#### Status
Accepted *(supersedes Cloudflare Access & Tunnel deployment details in [v1 ADR 5](architecture-decisions.md#adr-5-compile-time-split-public-and-admin-ktor-jvm-servers-and-separate-admin-ui))*

#### Context
v1 coupled `server/server-admin` authentication and private ingress to Cloudflare Access (`Cf-Access-Jwt-Assertion`) and Cloudflare Tunnel (`cloudflared`). Moving away from Cloudflare requires an authentication and ingress model that uses open industry standards, avoids vendor-specific headers, and supports **Google OAuth / OIDC** in production without adding a heavy self-hosted identity provider (such as Keycloak or Authentik) for a single-author CMS.

#### Decision
- **Retain Compile-Time Split Servers & Separate Admin UI:** `server/server-db`, `server/server-public`, `server/server-admin`, and `app/adminShared` / `app/adminDesktopApp` / `app/adminWebApp` remain unchanged in module topology and Konsist enforcement.
- **Standard OIDC/JWKS Authentication:** Replace Cloudflare-specific `Cf-Access-Jwt-Assertion` handling with standard **OAuth 2.0 / OpenID Connect (OIDC)** verification via `Authorization: Bearer <jwt>` against configurable provider settings (`OIDC_JWKS_URL`, `OIDC_ISSUER`, `OIDC_AUDIENCE`, `ADMIN_OWNER_ALLOWLIST`).
  - **Production IdP:** **Google OAuth / OIDC** (Authorization Code + PKCE for interactive admin login, RS256 JWKS verification on `server/server-admin`, enforced against an explicit owner email/subject allowlist).
  - **Local Development:** Retain the existing dev-profile HS256 bearer token workflow (`scripts/dev-token.sh`) for offline local/Bruno testing.
- **Ingress & Network Isolation:** Terminate TLS directly via **Cloud Run Managed HTTPS** (no Cloudflare Tunnel daemon). `server/server-admin` can optionally layer **Cloud Run IAM** or **Identity-Aware Proxy (IAP)** at the GCP perimeter while keeping the application itself dependent only on standard OIDC JWT verification.

#### Trade-offs & References
- **Trade-off:** Standard OIDC/JWKS requires implementing standard OAuth 2.0 PKCE token exchange in the admin client rather than relying on an edge proxy to inject a proprietary header, in exchange for zero vendor lock-in—switching from Google OAuth to GitHub, Auth0, or self-hosted Authentik/Keycloak later is purely a configuration change.
- OpenID Foundation — [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- IETF — [RFC 7517 (JSON Web Key)](https://datatracker.ietf.org/doc/html/rfc7517) and [RFC 7636 (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)

---

### ADR 9 (v2): Native GCS Object Storage Behind `BlobStorage`, Cookieless Analytics, and Ktor `/read/{slug}` SEO

#### Status
Accepted *(supersedes Cloudflare R2 dependency in [v1 ADR 9](architecture-decisions.md#adr-9-cloudflare-r2-presigned-uploads-cookieless-analytics-and-ktor-readslug-seo))*

#### Context
Binary resumes, slide decks, PDFs, and images must stay out of PostgreSQL `BYTEA` columns and avoid proxying large payloads through Ktor memory. Because production runs on GCP (Cloud Run), using GCS via S3-compatible interoperability would require managing static HMAC access keys and pulling an AWS SDK dependency into a GCP-native stack.

#### Decision
- **Native Google Cloud Storage (`GcsBlobStorage`):** Implement a native **Google Cloud Storage** adapter (`GcsBlobStorage` using `com.google.cloud:google-cloud-storage`) behind the existing `BlobStorage` interface in `server/server-db`.
  - **Keyless Production Auth:** On Cloud Run, authenticate via **Application Default Credentials (ADC)** and the Cloud Run service account's IAM permissions (`roles/storage.objectAdmin` scoped to the bucket + IAM `signBlob` for GCS V4 signed URLs)—eliminating static storage access keys and key rotation entirely.
  - **Hexagonal Isolation:** Business logic in `server/server-public` and `server/server-admin` depends strictly on the `BlobStorage` interface (zero GCS SDK imports outside `server/server-db`). Local Docker/Testcontainers can run against a local emulator (`fake-gcs-server` or the local `BlobStorage` test adapter) without cloud credentials.
  - Retain the 3-step presigned upload lifecycle: client SHA-256 → `POST /api/v1/admin/documents/presign` → direct `PUT` to GCS V4 signed URL → `POST /api/v1/admin/documents/confirm` (metadata existence check + magic-byte MIME verification).
- **Cookieless Analytics & `/read/{slug}` SEO:** Unchanged from v1 (`analytics.events` with daily-salted SHA-256 `visitor_hash`, `analytics.daily_rollups`, dynamic `wasmJs` HTML shell metadata, `/sitemap.xml`, `/robots.txt`, and Ktor HTML DSL Reader View at `GET /read/{slug}`).

#### Trade-offs & References
- **Trade-off:** Using the native GCS SDK introduces a GCS-specific adapter in `server/server-db` (rather than a single S3 client shared with MinIO), rewarded by **zero static storage credentials** in production, idiomatic GCP IAM least privilege, and native GCS V4 signed URLs—while keeping all domain/service layers decoupled behind `BlobStorage`.
- Google Cloud — [Cloud Storage Java Client](https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java) and [V4 Signed URLs](https://cloud.google.com/storage/docs/access-control/signing-urls-with-helpers)

---

## New Infrastructure & Operations Decisions

### ADR 11: GCP Runtime Architecture (Cloud Run, Cloud Run Jobs, Cloud SQL, and Managed HTTPS)

#### Status
Accepted

#### Context
The backend consists of two stateless HTTP Ktor JVM services with very different traffic profiles (`server-public` serves visitors/crawlers; `server-admin` is a low-traffic, single-author CMS), a one-shot Flyway schema migrator (`db-migrator`), and a PostgreSQL 16 database. Hosting these on a self-managed Linux VM with Docker Compose (or Kubernetes) requires ongoing OS patching, reverse-proxy/TLS maintenance, manual zero-downtime rollout scripting, and 24/7 idle compute cost for `server-admin`.

#### Decision
Deploy the containerized backend on **Google Cloud Platform (GCP)** using managed services that speak open container and database protocols:
- **Stateless Services (`Cloud Run`):**
  - `server/server-public`: Deployed as a Cloud Run service using its existing multi-stage non-root OCI `Dockerfile`. Scales automatically; `min_instance_count` can be tuned between `0` (lowest cost, ~2–4s JVM cold start) and `1` (warm latency for visitors and SEO crawlers).
  - `server/server-admin`: Deployed as a separate Cloud Run service with `min_instance_count = 0` so idle CMS cost is effectively \$0/month.
- **Schema Migrations (`Cloud Run Jobs`):**
  - `db-migrator`: Deployed as a one-shot **Cloud Run Job** executed during CI/CD releases before traffic shifts to new server revisions, preserving Flyway advisory locking (`pg_advisory_lock`) and least-privilege `portfolio_migrator` credentials.
- **Database (`Cloud SQL for PostgreSQL 16`):**
  - Managed PostgreSQL 16 with automated backups and point-in-time recovery, connected from Cloud Run via the built-in **Cloud SQL Auth Proxy Unix socket** (avoiding public DB exposure or NAT Gateway costs).
  - All schemas (`portfolio`, `analytics`), 3-tier roles (`portfolio_migrator`, `portfolio_admin`, `portfolio_public`), and queries remain 100% standard PostgreSQL 16.
- **Edge & TLS (`Cloud Run Managed HTTPS`):**
  - Use Cloud Run's built-in managed TLS and custom domain mapping directly (no Cloudflare CDN or Tunnel required).
- **Container Registry (`Artifact Registry`):**
  - Store versioned immutable OCI images tagged by Git SHA.

#### Portability & Future Escape Hatch
Cloud Run implements the open-source **Knative** container contract. Because `server-public`, `server-admin`, and `db-migrator` are standard OCI images with no GCP runtime SDK dependencies, migrating to self-hosted **Docker Compose + Caddy** on a Linux VM or **Kubernetes / k3s** later requires zero application changes.

#### References
- Google Cloud — [Cloud Run](https://cloud.google.com/run/docs), [Cloud Run Jobs](https://cloud.google.com/run/docs/create-jobs), and [Cloud SQL for PostgreSQL](https://cloud.google.com/sql/docs/postgres)
- Knative — [Knative Serving Specification](https://knative.dev/docs/)

---

### ADR 12: Infrastructure as Code with Terraform and GCP Secret Manager

#### Status
Accepted

#### Context
Manually provisioning GCP projects, APIs, Cloud SQL instances, IAM bindings, Artifact Registry, GCS buckets, secrets, and Cloud Run services leads to configuration drift and irreproducible environments. Secrets must never be committed to Git or baked into container images.

#### Decision
- **Infrastructure as Code (`Terraform`):**
  - Provision all GCP infrastructure declaratively under `infra/terraform/` using **HashiCorp Terraform** with the official `hashicorp/google` provider.
  - Store Terraform state remotely in a dedicated, versioned **GCS bucket** (`backend "gcs"`) with native state locking.
  - Enable required GCP APIs explicitly via `google_project_service` with `depends_on` guards.
  - Use strictly **additive IAM resources** (`*_iam_member`, never authoritative `*_iam_policy` or `*_iam_binding`) to follow least privilege and avoid clobbering Google-managed service agents.
  - Separate per-environment variable files (`dev` / `prod`) cleanly without duplicating module logic.
- **Secrets Management (`GCP Secret Manager` + 12-Factor Env Injection):**
  - Store runtime secrets (DB role passwords, Google OAuth client secret, analytics salt) in **GCP Secret Manager** *(note: GCS uses keyless Cloud Run ADC/IAM rather than static keys)*.
  - Mount secrets into Cloud Run services and jobs as standard environment variables via Terraform (`secret_key_ref`). Application code reads **only** `System.getenv(...)` and maintains `.env.example` parity—never importing a cloud-specific Secret Manager SDK.

#### Trade-offs & References
- **Trade-off:** Terraform uses the BSL 1.1 license rather than pure OSS OpenTofu, chosen for industry-standard familiarity; because HCL syntax and the `hashicorp/google` provider are compatible, switching to OpenTofu later remains trivial.
- HashiCorp — [Terraform Google Provider](https://registry.terraform.io/providers/hashicorp/google/latest/docs)
- The Twelve-Factor App — [Config](https://12factor.net/config)

---

### ADR 13: Vendor-Neutral Observability via OpenTelemetry (GCP Cloud Logging/Monitoring First, OSS-Ready)

#### Status
Accepted

#### Context
Prometheus + Grafana (+ Loki/Tempo) is the industry-standard open-source observability stack, but self-hosting it requires additional always-on compute and persistent storage that is disproportionate for a personal portfolio. At the same time, instrumenting application code with proprietary GCP logging or monitoring SDKs would create avoidable vendor lock-in.

#### Decision
- **Open-Standard Instrumentation:** Emit structured **JSON logs to `stdout`** (correlation/request IDs, route, status, latency, sanitized errors) and instrument Ktor JVM services with **OpenTelemetry (OTel)** for metrics and traces (with standard OTLP export and optional `/metrics` Prometheus scrape compatibility).
- **Initial Backend (`GCP Cloud Logging & Cloud Monitoring`):** Use Cloud Run's zero-config ingestion of `stdout` JSON logs and OTLP telemetry into GCP Cloud Logging/Monitoring to stay within GCP's free tier with zero extra containers to maintain.
- **OSS Portability:** Because instrumentation is strictly OTel/JSON, switching to **Grafana + Prometheus + Loki** (either self-hosted or Grafana Cloud's free tier) requires changing only exporter environment variables (`OTEL_EXPORTER_OTLP_ENDPOINT`)—zero code changes.

#### References
- OpenTelemetry — [OpenTelemetry Java / Kotlin Documentation](https://opentelemetry.io/docs/languages/java/)
- Google Cloud — [Cloud Logging Structured Logging](https://cloud.google.com/logging/docs/structured-logging) and [OpenTelemetry on GCP](https://cloud.google.com/stackdriver/docs/instrumentation/overview)

---

### ADR 14: Unified Multi-Platform CI/CD with CircleCI and Keyless GCP OIDC

#### Status
Accepted

#### Context
This 13-module monorepo targets **JVM servers, WasmJS web, JVM Desktop, Android, and native SwiftUI iOS**, plus **Terraform** and **Docker**. Many CI platforms handle either Linux/cloud deploys *or* mobile/macOS well, but not both, and maintaining separate CI systems increases operational friction. Meanwhile, CI pipeline logic should not become tightly coupled to any single CI provider's proprietary YAML DSL.

#### Decision
- **Single Unified Platform (`CircleCI`):** Adopt **CircleCI** (`.circleci/config.yml`) as the single CI/CD platform covering every target in the monorepo:
  - **Linux executors:** Run `./scripts/verify.sh --fast` (JVM tests, Ktor contract tests, Konsist fitness checks, script syntax), Docker multi-stage builds, Testcontainers integration tests, `wasmJs` web builds, Android builds/tests, and Terraform `plan`/`apply`.
  - **macOS executors (path-filtered):** Run iOS simulator builds/tests (`./kotlin test -m iosApp`) and signing/release workflows **only** when paths affecting `app/iosApp` or shared KMP modules change (or on release tags), avoiding unnecessary macOS credit burn on backend/infra PRs.
- **Keyless Cloud Authentication:** Authenticate CircleCI to GCP using **CircleCI OIDC tokens + GCP Workload Identity Federation**—never storing long-lived GCP service account JSON keys in CI secrets.
- **Thin CI Wrapper (Anti-Lock-In):** Keep build, test, and smoke-test orchestration inside repository-owned scripts (`./kotlin`, `scripts/verify.sh`, `scripts/smoke.sh`). CircleCI jobs act strictly as thin orchestrators calling repo scripts, keeping migration cost low if CI providers ever change.
- **Ordered Safe Deployment Pipeline (on `main`):**
  1. Build & push immutable Docker images (`server-public`, `server-admin`, `db-migrator`) to Artifact Registry tagged by Git SHA.
  2. Execute `db-migrator` **Cloud Run Job** and wait for exit `0`.
  3. Update `server-public` and `server-admin` **Cloud Run** services to the new SHA and verify `/health/ready`.

#### Trade-offs & References
- **Trade-off:** CircleCI configuration is CircleCI-specific YAML, mitigated by keeping all build/verification logic inside repo-owned scripts. On macOS jobs, path filtering and dependency caching are essential to keep build times and credit usage predictable.
- CircleCI — [OIDC Tokens with GCP Workload Identity Federation](https://circleci.com/docs/openid-connect-tokens/) and [Dynamic Configuration / Path Filtering](https://circleci.com/docs/using-dynamic-configuration/)

---

## Implementation Follow-Up Checklist (v1 → v2 Delta)

> These items record the concrete codebase and infrastructure work required to implement the v2 decisions above. None are marked complete until implemented and verified.

- [ ] **Admin Auth (`ADR 5 v2`):** Refactor `AdminAuthenticator.kt`, `AdminAuthPlugin.kt`, `Application.kt`, and `app/adminShared` (`AdminHttpClientFactory`, `AdminSignInScreen`) to replace `Cf-Access-Jwt-Assertion` with generic OIDC/JWKS (`OIDC_JWKS_URL`, `OIDC_ISSUER`, `OIDC_AUDIENCE`) + Google OAuth PKCE flow; keep dev-profile HS256 token support.
- [ ] **Native GCS Storage (`ADR 9 v2`):** Implement `GcsBlobStorage` (`com.google.cloud:google-cloud-storage`, keyless ADC + V4 signed URLs) behind `BlobStorage` in `server/server-db`, configure local emulator support (`fake-gcs-server` or local adapter) in `compose.yaml`, and update `.env.example` and `app/adminShared` UI copy from Cloudflare R2 to GCS.
- [ ] **Terraform IaC (`ADR 11` & `ADR 12`):** Create `infra/terraform/` provisioning GCP APIs, Artifact Registry, Cloud SQL (PostgreSQL 16), GCS bucket (with scoped service-account IAM + `iam.serviceAccountTokenCreator` for V4 signing), Secret Manager, Cloud Run (`server-public`, `server-admin`), Cloud Run Job (`db-migrator`), Workload Identity Federation pool for CircleCI, and least-privilege `*_iam_member` bindings.
- [ ] **Observability (`ADR 13`):** Add structured JSON logging + OpenTelemetry instrumentation to `server/server-public` and `server/server-admin` with OTLP exporter configuration.
- [ ] **CircleCI Pipeline (`ADR 14`):** Add `.circleci/config.yml` with path-filtered Linux (JVM/Konsist/Docker/Terraform/Android) and macOS (iOS) workflows plus keyless GCP Workload Identity Federation deploy steps.
- [ ] **Plan Sync:** Reflect the v2 cloud/IaC/CI phases in [01_PLAN.md](01_PLAN.md) once implementation begins.
