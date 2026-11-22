# Dedicated VPC with Private Services Access so Cloud SQL has no public IP
# and Cloud Run reaches Postgres directly over Direct VPC Egress (zero NAT/connector cost).

resource "google_compute_network" "main" {
  name                    = "${var.name_prefix}-${var.environment}-vpc"
  auto_create_subnetworks = false

  depends_on = [google_project_service.enabled]
}

resource "google_compute_subnetwork" "cloud_run" {
  name                     = "${var.name_prefix}-${var.environment}-run-subnet"
  region                   = var.region
  network                  = google_compute_network.main.id
  ip_cidr_range            = "10.10.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_global_address" "private_service_range" {
  name          = "${var.name_prefix}-${var.environment}-sql-range"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 20
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_service_range.name]

  depends_on = [google_project_service.enabled]
}
