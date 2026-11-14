# Shared by every environment: the state stays next to the environment (not committed), and the
# release to run comes from the environment
remote_state {
  backend = "local"
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite"
  }
  config = {
    path = "${get_terragrunt_dir()}/terraform.tfstate"
  }
}

inputs = {
  region         = "europe-west1"
  dockerhub_user = get_env("DOCKERHUB_USERNAME")
  image_version  = get_env("RELAY_VERSION")
}
