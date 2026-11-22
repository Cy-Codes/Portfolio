locals {
  jdbc_base_url = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/${var.db_name}?sslmode=require"
}

# ------------------------------------------------------------------------------
# One-Shot Flyway Schema Migrator (Cloud Run Job)
# ------------------------------------------------------------------------------

resource "google_cloud_run_v2_job" "db_migrator" {
  name                = "${var.name_prefix}-${var.environment}-db-migrator"
  location            = var.region
  deletion_protection = false

  template {
    task_count = 1

    template {
      service_account = google_service_account.db_migrator.email
      max_retries     = 1
      timeout         = "300s"

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = google_compute_network.main.name
          subnetwork = google_compute_subnetwork.cloud_run.name
        }
      }

      containers {
        image = var.migrator_image

        resources {
          limits = {
            cpu    = "1"
            memory = "512Mi"
          }
        }

        env {
          name  = "FLYWAY_URL"
          value = local.jdbc_base_url
        }

        env {
          name  = "FLYWAY_USER"
          value = "portfolio_migrator"
        }

        env {
          name = "FLYWAY_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.db_migrator_password.secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  depends_on = [
    google_project_service.enabled,
    google_secret_manager_secret_iam_member.migrator_password_accessor,
    google_sql_database.portfolio,
  ]
}

# ------------------------------------------------------------------------------
# Protected Admin Server (Cloud Run Service — scales to zero when idle)
# ------------------------------------------------------------------------------

resource "google_cloud_run_v2_service" "server_admin" {
  name                = "${var.name_prefix}-${var.environment}-server-admin"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = var.environment == "prod"

  template {
    service_account = google_service_account.server_admin.email

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.main.name
        subnetwork = google_compute_subnetwork.cloud_run.name
      }
    }

    containers {
      image = var.admin_image

      ports {
        name           = "http1"
        container_port = 8081
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      startup_probe {
        http_get {
          path = "/health"
          port = 8081
        }
        initial_delay_seconds = 5
        period_seconds        = 5
        timeout_seconds       = 3
        failure_threshold     = 12
      }

      liveness_probe {
        http_get {
          path = "/health"
          port = 8081
        }
        period_seconds    = 30
        timeout_seconds   = 3
        failure_threshold = 3
      }

      env {
        name  = "APP_ENV"
        value = var.environment
      }

      env {
        name  = "ADMIN_SERVER_HOST"
        value = "0.0.0.0"
      }

      env {
        name  = "ADMIN_SERVER_PORT"
        value = "8081"
      }

      env {
        name  = "JDBC_DATABASE_URL"
        value = "${local.jdbc_base_url}&reWriteBatchedInserts=true"
      }

      env {
        name  = "PORTFOLIO_ADMIN_USER"
        value = "portfolio_admin"
      }

      env {
        name = "PORTFOLIO_ADMIN_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_admin_password.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "ADMIN_SESSION_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.admin_session_secret.secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "ADMIN_ALLOWED_SUBJECTS"
        value = var.admin_allowed_subjects
      }

      env {
        name  = "ADMIN_CORS_ORIGINS"
        value = var.admin_cors_origins
      }

      env {
        name  = "OAUTH_GOOGLE_CLIENT_ID"
        value = var.oauth_google_client_id
      }

      env {
        name  = "GCS_BUCKET_NAME"
        value = google_storage_bucket.assets.name
      }
    }
  }

  depends_on = [
    google_project_service.enabled,
    google_secret_manager_secret_iam_member.admin_db_password_accessor,
    google_secret_manager_secret_iam_member.admin_session_secret_accessor,
    google_sql_database.portfolio,
  ]
}

# ------------------------------------------------------------------------------
# Cloud Run Invoker IAM (Locked Down by Default)
# ------------------------------------------------------------------------------

resource "google_cloud_run_v2_service_iam_member" "admin_invokers" {
  count = var.admin_public_access ? 0 : length(var.admin_invoker_members)

  location = google_cloud_run_v2_service.server_admin.location
  name     = google_cloud_run_v2_service.server_admin.name
  role     = "roles/run.invoker"
  member   = var.admin_invoker_members[count.index]
}

resource "google_cloud_run_v2_service_iam_member" "admin_public_invoker" {
  count = var.admin_public_access ? 1 : 0

  location = google_cloud_run_v2_service.server_admin.location
  name     = google_cloud_run_v2_service.server_admin.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
