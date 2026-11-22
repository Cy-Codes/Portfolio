locals {
  admin_cors_origin_list = [
    for origin in split(",", var.admin_cors_origins) : trimspace(origin)
    if trimspace(origin) != ""
  ]
}

resource "google_storage_bucket" "assets" {
  name                        = "${var.project_id}-${var.name_prefix}-${var.environment}-assets"
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = var.environment != "prod"

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 3
    }
    action {
      type = "Delete"
    }
  }

  dynamic "cors" {
    for_each = length(local.admin_cors_origin_list) > 0 ? [1] : []
    content {
      origin          = local.admin_cors_origin_list
      method          = ["GET", "HEAD", "PUT"]
      response_header = ["Content-Type", "ETag", "x-goog-meta-*"]
      max_age_seconds = 3600
    }
  }

  depends_on = [google_project_service.enabled]
}
