include "root" {
  path = find_in_parent_folders("root.hcl")
}

terraform {
  source = "../../modules/push-relay"
}

inputs = {
  project_id = "lockate-staging"
}
