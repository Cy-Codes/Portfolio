output "artifact_registry_url" {
  description = "Docker registry base URL for pushing immutable SHA-tagged images."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.containers.repository_id}"
}

output "admin_service_url" {
  description = "Managed HTTPS URL for server-admin on Cloud Run."
  value       = google_cloud_run_v2_service.server_admin.uri
}

output "migrator_job_name" {
  description = "Cloud Run Job name for executing one-shot Flyway migrations."
  value       = google_cloud_run_v2_job.db_migrator.name
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL instance connection name."
  value       = google_sql_database_instance.main.connection_name
}

output "cloud_sql_private_ip" {
  description = "Private VPC IP address of the Cloud SQL PostgreSQL 16 instance."
  value       = google_sql_database_instance.main.private_ip_address
}

output "assets_bucket_name" {
  description = "Native GCS bucket name for portfolio documents and assets."
  value       = google_storage_bucket.assets.name
}

output "admin_service_account_email" {
  description = "Least-privilege service account email attached to server-admin."
  value       = google_service_account.server_admin.email
}
