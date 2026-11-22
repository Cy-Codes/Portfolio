# Secret Manager containers ONLY — never commit google_secret_manager_secret_version
# values into this public repository. Populate values out-of-band via:
#   printf '%s' '<value>' | gcloud secrets versions add <secret-id> --data-file=-

resource "google_secret_manager_secret" "db_migrator_password" {
  secret_id = "${var.name_prefix}-${var.environment}-db-migrator-password"

  replication {
    auto {}
  }

  depends_on = [google_project_service.enabled]
}

resource "google_secret_manager_secret" "db_admin_password" {
  secret_id = "${var.name_prefix}-${var.environment}-db-admin-password"

  replication {
    auto {}
  }

  depends_on = [google_project_service.enabled]
}

resource "google_secret_manager_secret" "admin_session_secret" {
  secret_id = "${var.name_prefix}-${var.environment}-admin-session-secret"

  replication {
    auto {}
  }

  depends_on = [google_project_service.enabled]
}

# --- Scoped Secret Accessors (strictly additive *_iam_member) ---

resource "google_secret_manager_secret_iam_member" "migrator_password_accessor" {
  secret_id = google_secret_manager_secret.db_migrator_password.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.db_migrator.email}"
}

resource "google_secret_manager_secret_iam_member" "admin_db_password_accessor" {
  secret_id = google_secret_manager_secret.db_admin_password.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.server_admin.email}"
}

resource "google_secret_manager_secret_iam_member" "admin_session_secret_accessor" {
  secret_id = google_secret_manager_secret.admin_session_secret.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.server_admin.email}"
}
