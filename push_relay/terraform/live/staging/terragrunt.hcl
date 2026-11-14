include "root" {
  path = find_in_parent_folders("root.hcl")
}

terraform {
  source = "../../modules/push-relay"
}

inputs = {
  project_id = "lockate-staging"
  # The key the dev Docker Compose relay reads (git-ignored)
  dev_key_file = "${get_repo_root()}/push_relay/.secrets/service-account.json"
}
