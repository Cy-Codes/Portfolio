terraform {
  required_version = ">= 1.9.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }

  # Remote GCS state backend. Pass bucket and prefix via `-backend-config`
  # so production state bucket names are never hardcoded in this public repo.
  backend "gcs" {}
}

provider "google" {
  project = var.project_id
  region  = var.region
}
