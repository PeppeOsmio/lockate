--liquibase formatted sql

--changeset lockate:passwordless-groups
-- Groups were protected by a password that no longer exists, so they can't be joined anymore
DELETE FROM anonymous_group;
ALTER TABLE anonymous_group DROP COLUMN key_salt;
ALTER TABLE anonymous_group DROP COLUMN member_password_srp_salt;
ALTER TABLE anonymous_group DROP COLUMN member_password_srp_verifier;
