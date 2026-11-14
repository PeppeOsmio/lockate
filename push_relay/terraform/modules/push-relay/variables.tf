variable "project_id" {
  type    = string
  default = "lockate"
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "dockerhub_user" {
  type        = string
  description = "The Docker Hub namespace the release workflow publishes to (DOCKERHUB_USERNAME)"
}

variable "image_version" {
  type        = string
  description = "The release version of lockate-push-relay to run, like 1.2.3"
}

variable "max_instances" {
  type        = number
  default     = 3
  description = "Caps the cost: the rate limits are per instance"
}
