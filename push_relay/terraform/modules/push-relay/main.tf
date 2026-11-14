terraform {
  required_version = ">= 1.6"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

resource "google_project_service" "apis" {
  for_each           = toset(["run.googleapis.com", "artifactregistry.googleapis.com", "fcm.googleapis.com"])
  service            = each.key
  disable_on_destroy = false
}

resource "google_service_account" "relay" {
  account_id   = "push-relay"
  display_name = "Lockate push relay"
}

resource "google_project_iam_member" "relay_fcm" {
  project = var.project_id
  role    = "roles/firebasecloudmessaging.admin"
  member  = "serviceAccount:${google_service_account.relay.email}"
}

# Cloud Run can't pull from Docker Hub directly: this repository proxies it
resource "google_artifact_registry_repository" "dockerhub" {
  repository_id = "dockerhub"
  location      = var.region
  format        = "DOCKER"
  mode          = "REMOTE_REPOSITORY"

  remote_repository_config {
    docker_repository {
      public_repository = "DOCKER_HUB"
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_v2_service" "relay" {
  name                = "lockate-push-relay"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.relay.email

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.dockerhub.repository_id}/${var.dockerhub_user}/lockate-push-relay:${var.image_version}"

      resources {
        limits = {
          memory = "256Mi"
          cpu    = "1"
        }
      }

      env {
        name  = "FCM_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "RELAY_BEHIND_PROXY"
        value = "true"
      }
    }
  }

  depends_on = [google_project_iam_member.relay_fcm]
}

# The relay has no authentication: the FCM token is the permission
resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.relay.name
  location = google_cloud_run_v2_service.relay.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

output "url" {
  description = "Value for the backend's PUSH_RELAY_URL"
  value       = google_cloud_run_v2_service.relay.uri
}
