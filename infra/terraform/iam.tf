# Dedicated least-privilege runtime service accounts (strictly additive *_iam_member bindings).

resource "google_service_account" "server_admin" {
  account_id   = "${var.name_prefix}-${var.environment}-admin"
  display_name = "Portfolio Admin Server (${var.environment})"

  depends_on = [google_project_service.enabled]
}

resource "google_service_account" "db_migrator" {
  account_id   = "${var.name_prefix}-${var.environment}-migrator"
  display_name = "Portfolio Flyway DB Migrator (${var.environment})"

  depends_on = [google_project_service.enabled]
}

# --- server-admin: native GCS access + keyless V4 URL signing (self signBlob) ---

resource "google_storage_bucket_iam_member" "admin_assets_object_admin" {
  bucket = google_storage_bucket.assets.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.server_admin.email}"
}

resource "google_service_account_iam_member" "admin_self_token_creator" {
  service_account_id = google_service_account.server_admin.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.server_admin.email}"
}

# --- Observability (Cloud Logging + Cloud Monitoring) ---

resource "google_project_iam_member" "admin_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.server_admin.email}"
}

resource "google_project_iam_member" "admin_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.server_admin.email}"
}

resource "google_project_iam_member" "migrator_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.db_migrator.email}"
}
