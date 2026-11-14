terraform {
  required_version = ">= 1.6"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  # Bill and count quota on the environment's own project, whatever the gcloud login's quota project
  user_project_override = true
  billing_project       = var.project_id
}

locals {
  # Cloud Run and Artifact Registry need the Blaze plan; FCM and service accounts don't
  apis = concat(["fcm.googleapis.com", "iam.googleapis.com"], var.cloud_run ? ["run.googleapis.com", "artifactregistry.googleapis.com"] : [])
}

resource "google_project_service" "apis" {
  for_each           = toset(local.apis)
  service            = each.key
  disable_on_destroy = false
}

# --- Cloud Run ---

resource "google_service_account" "relay" {
  count        = var.cloud_run ? 1 : 0
  account_id   = "push-relay"
  display_name = "Lockate push relay"
  depends_on   = [google_project_service.apis]
}

resource "google_project_iam_member" "relay_fcm" {
  count   = var.cloud_run ? 1 : 0
  project = var.project_id
  role    = "roles/firebasecloudmessaging.admin"
  member  = "serviceAccount:${google_service_account.relay[0].email}"
}

# Cloud Run can't pull from Docker Hub directly: this repository proxies it
resource "google_artifact_registry_repository" "dockerhub" {
  count         = var.cloud_run ? 1 : 0
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
  count               = var.cloud_run ? 1 : 0
  name                = "lockate-push-relay"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.relay[0].email

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.dockerhub[0].repository_id}/${var.dockerhub_user}/lockate-push-relay:${var.image_version}"

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

  lifecycle {
    precondition {
      condition     = var.dockerhub_user != "" && var.image_version != ""
      error_message = "Set DOCKERHUB_USERNAME and RELAY_VERSION to deploy the relay to Cloud Run."
    }
  }

  depends_on = [google_project_iam_member.relay_fcm]
}

# The relay has no authentication: the FCM token is the permission
resource "google_cloud_run_v2_service_iam_member" "public" {
  count    = var.cloud_run ? 1 : 0
  name     = google_cloud_run_v2_service.relay[0].name
  location = google_cloud_run_v2_service.relay[0].location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# --- Key for a relay run on a developer's machine ---

resource "google_service_account" "dev" {
  count        = var.dev_key_file != "" ? 1 : 0
  account_id   = "push-relay-dev"
  display_name = "Lockate push relay, local development"
  depends_on   = [google_project_service.apis]
}

resource "google_project_iam_member" "dev_fcm" {
  count   = var.dev_key_file != "" ? 1 : 0
  project = var.project_id
  role    = "roles/firebasecloudmessaging.admin"
  member  = "serviceAccount:${google_service_account.dev[0].email}"
}

# The private key is also kept in the Terraform state, which is why the state is never committed
resource "google_service_account_key" "dev" {
  count              = var.dev_key_file != "" ? 1 : 0
  service_account_id = google_service_account.dev[0].name
  # A key without the role can't push, and an apply targeting only the key file must grant it too
  depends_on = [google_project_iam_member.dev_fcm]
}

resource "local_sensitive_file" "dev_key" {
  count           = var.dev_key_file != "" ? 1 : 0
  filename        = var.dev_key_file
  content         = base64decode(google_service_account_key.dev[0].private_key)
  file_permission = "0600"
}

output "url" {
  description = "Value for the backend's PUSH_RELAY_URL"
  value       = var.cloud_run ? google_cloud_run_v2_service.relay[0].uri : null
}
