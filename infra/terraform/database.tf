# Cloud SQL for PostgreSQL 16 (Private IP Only + Enforced TLS).
# Three-tier least-privilege DB roles (`portfolio_migrator`, `portfolio_admin`,
# `portfolio_public`) are created out-of-band via `gcloud sql users create` using
# passwords stored in GCP Secret Manager so no database passwords ever enter Terraform state.

resource "google_sql_database_instance" "main" {
  name                = "${var.name_prefix}-${var.environment}-pg16"
  database_version    = "POSTGRES_16"
  region              = var.region
  deletion_protection = var.db_deletion_protection

  settings {
    tier              = var.db_tier
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.db_disk_size_gb
    disk_autoresize   = true

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = var.environment == "prod"
      transaction_log_retention_days = var.environment == "prod" ? 7 : 3

      backup_retention_settings {
        retained_backups = var.environment == "prod" ? 14 : 7
        retention_unit   = "COUNT"
      }
    }

    maintenance_window {
      day          = 7 # Sunday
      hour         = 4
      update_track = "stable"
    }

    insights_config {
      query_insights_enabled = true
    }
  }

  depends_on = [
    google_project_service.enabled,
    google_service_networking_connection.private_vpc_connection,
  ]
}

resource "google_sql_database" "portfolio" {
  name     = var.db_name
  instance = google_sql_database_instance.main.name
}
