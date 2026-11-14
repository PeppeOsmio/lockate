variable "project_id" {
  type = string
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "cloud_run" {
  type        = bool
  default     = true
  description = "Deploy the relay to Cloud Run; needs the project on the Blaze plan"
}

variable "dockerhub_user" {
  type        = string
  default     = ""
  description = "The Docker Hub namespace the release workflow publishes to (DOCKERHUB_USERNAME); needed with cloud_run"
}

variable "image_version" {
  type        = string
  default     = ""
  description = "The release version of lockate-push-relay to run, like 1.2.3; needed with cloud_run"
}

variable "max_instances" {
  type        = number
  default     = 3
  description = "Caps the cost: the rate limits are per instance"
}

variable "dev_key_file" {
  type        = string
  default     = ""
  description = "Where to write a key of a push-relay-dev service account, for a relay run locally; empty creates none"
}
