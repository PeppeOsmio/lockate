--liquibase formatted sql

--changeset lockate:push-registration
ALTER TABLE ag_member ADD COLUMN push_token VARCHAR(4096);
ALTER TABLE ag_member ADD COLUMN push_registration_id BYTEA;
ALTER TABLE ag_member ADD COLUMN push_key BYTEA;
