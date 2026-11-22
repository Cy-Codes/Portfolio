# Portfolio GCP Infrastructure (`infra/terraform/`)

Provisions the initial GCP foundation per [architecture-decisions-v2.md](../../docs/architecture-decisions-v2.md) (`ADR 11`–`ADR 12`):

- **Private VPC + Direct VPC Egress** (`network.tf`) — zero Serverless VPC Connector fee, zero NAT Gateway
- **Cloud SQL for PostgreSQL 16** (`database.tf`) — private IP only (`ipv4_enabled = false`), enforced TLS (`ENCRYPTED_ONLY`), automated backups & PITR
- **Native GCS Assets Bucket** (`storage.tf`) — Uniform Bucket-Level Access, Public Access Prevention enforced, keyless IAM V4 URL signing
- **Artifact Registry** (`artifact_registry.tf`) — Docker repo with automatic cleanup policies
- **GCP Secret Manager shells** (`secrets.tf`) — secret containers only; values populated out-of-band
- **Cloud Run Job (`db-migrator`) & Cloud Run Service (`server-admin`)** (`cloud_run.tf`) — scales to zero, locked down via GCP IAM by default

---

## One-Time Bootstrap Runbook

### 1. Create the Remote Terraform State Bucket (Out-of-Band)

```bash
export PROJECT_ID="your-gcp-project-id"
export REGION="us-central1"
export STATE_BUCKET="${PROJECT_ID}-tfstate"

gcloud storage buckets create "gs://${STATE_BUCKET}" \
  --project="${PROJECT_ID}" \
  --location="${REGION}" \
  --uniform-bucket-level-access \
  --public-access-prevention

gcloud storage buckets update "gs://${STATE_BUCKET}" --versioning
```

### 2. Initialize Terraform with Remote State

```bash
cd infra/terraform
terraform init \
  -backend-config="bucket=${STATE_BUCKET}" \
  -backend-config="prefix=portfolio/prod"
```

### 3. Bootstrap Foundation Resources First (APIs, Registry, Secrets, DB)

Because Cloud Run references Secret Manager versions and container images that must exist before the first revision starts, apply the foundation targets first:

```bash
cp terraform.tfvars.example prod.tfvars
# Edit git-ignored prod.tfvars with your real values

terraform apply -var-file=prod.tfvars \
  -target=google_project_service.enabled \
  -target=google_artifact_registry_repository.containers \
  -target=google_secret_manager_secret.db_migrator_password \
  -target=google_secret_manager_secret.db_admin_password \
  -target=google_secret_manager_secret.admin_session_secret \
  -target=google_sql_database.portfolio \
  -target=google_storage_bucket.assets
```

### 4. Populate Secret Versions Out-of-Band (Never in Git or Terraform State)

```bash
openssl rand -base64 32 | tr -d '\n' | gcloud secrets versions add portfolio-prod-db-migrator-password --data-file=-
openssl rand -base64 32 | tr -d '\n' | gcloud secrets versions add portfolio-prod-db-admin-password --data-file=-
openssl rand -base64 48 | tr -d '\n' | gcloud secrets versions add portfolio-prod-admin-session-secret --data-file=-
```

### 5. Create Least-Privilege PostgreSQL Roles in Cloud SQL

```bash
gcloud sql users create portfolio_migrator \
  --instance=portfolio-prod-pg16 \
  --password="$(gcloud secrets versions access latest --secret=portfolio-prod-db-migrator-password)"

gcloud sql users create portfolio_admin \
  --instance=portfolio-prod-pg16 \
  --password="$(gcloud secrets versions access latest --secret=portfolio-prod-db-admin-password)"
```

*(Grant `portfolio_migrator` `CREATE` on database `portfolio` once via Cloud SQL Studio or `gcloud sql connect`, and optionally create `portfolio_public` before running `V1` so `V1__init_schemas_roles_and_tables.sql` applies all three-tier grants cleanly.)*

### 6. Build & Push Immutable SHA-Tagged Images

```bash
export GIT_SHA="$(git rev-parse --short HEAD)"
export REGISTRY="${REGION}-docker.pkg.dev/${PROJECT_ID}/portfolio-prod"

gcloud auth configure-docker "${REGION}-docker.pkg.dev"

docker build --platform linux/amd64 -f server/server-db/Dockerfile -t "${REGISTRY}/db-migrator:${GIT_SHA}" .
docker build --platform linux/amd64 -f server/server-admin/Dockerfile -t "${REGISTRY}/server-admin:${GIT_SHA}" .

docker push "${REGISTRY}/db-migrator:${GIT_SHA}"
docker push "${REGISTRY}/server-admin:${GIT_SHA}"
```

### 7. Apply Full Stack & Execute Flyway Migration Job

Update `admin_image` and `migrator_image` in your git-ignored `prod.tfvars` to use `${GIT_SHA}`, then:

```bash
terraform apply -var-file=prod.tfvars

gcloud run jobs execute portfolio-prod-db-migrator \
  --region="${REGION}" \
  --wait
```

### 8. Verify Secured Admin Server

With `admin_public_access = false`, unauthenticated requests are rejected at the GCP IAM perimeter (`403`). Verify with your authenticated GCP identity token:

```bash
ADMIN_URL="$(terraform output -raw admin_service_url)"
curl -fsS -H "Authorization: Bearer $(gcloud auth print-identity-token)" "${ADMIN_URL}/health/ready"
```
