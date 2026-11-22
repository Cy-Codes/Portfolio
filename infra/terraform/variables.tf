variable "project_id" {
  description = "Target GCP project ID (supply via TF_VAR_project_id or git-ignored *.tfvars)."
  type        = string
}

variable "region" {
  description = "Primary GCP region for Cloud Run, Cloud SQL, Artifact Registry, and GCS."
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Deployment environment label (dev, staging, prod)."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "name_prefix" {
  description = "Short lowercase prefix applied to GCP resources."
  type        = string
  default     = "portfolio"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.name_prefix))
    error_message = "name_prefix must be 2-21 lowercase alphanumeric/hyphen characters starting with a letter."
  }
}

# ------------------------------------------------------------------------------
# Database (Cloud SQL for PostgreSQL 16)
# ------------------------------------------------------------------------------

variable "db_tier" {
  description = "Cloud SQL machine tier (e.g., db-f1-micro for low-cost initial deploy, db-g1-small for higher load)."
  type        = string
  default     = "db-f1-micro"
}

variable "db_disk_size_gb" {
  description = "Initial Cloud SQL SSD storage size in GB."
  type        = number
  default     = 10
}

variable "db_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "portfolio"
}

variable "db_deletion_protection" {
  description = "Prevent accidental Cloud SQL instance deletion via Terraform."
  type        = bool
  default     = true
}

# ------------------------------------------------------------------------------
# Container Images (Immutable SHA-Tagged Artifact Registry URIs)
# ------------------------------------------------------------------------------

variable "admin_image" {
  description = "Immutable Artifact Registry image URI for server/server-admin (never use :latest)."
  type        = string
}

variable "migrator_image" {
  description = "Immutable Artifact Registry image URI for server/server-db Flyway migrator (never use :latest)."
  type        = string
}

# ------------------------------------------------------------------------------
# Admin Server Security & Access
# ------------------------------------------------------------------------------

variable "admin_allowed_subjects" {
  description = "Comma-separated owner email/subject allowlist for ADMIN_ALLOWED_SUBJECTS."
  type        = string
  sensitive   = true
}

variable "admin_cors_origins" {
  description = "Comma-separated allowed CORS origins for server-admin (e.g., https://admin.example.com)."
  type        = string
  default     = ""
}

variable "oauth_google_client_id" {
  description = "Google OAuth 2.0 client ID for admin authentication (optional until OIDC flow is enabled)."
  type        = string
  default     = ""
}

variable "admin_public_access" {
  description = "When false (recommended default), Cloud Run IAM blocks unauthenticated HTTP requests to server-admin. Set true only when relying on application-level OIDC + CORS."
  type        = bool
  default     = false
}

variable "admin_invoker_members" {
  description = "IAM members (e.g., user:you@example.com) granted roles/run.invoker on server-admin when admin_public_access is false."
  type        = list(string)
  default     = []
  sensitive   = true
}
