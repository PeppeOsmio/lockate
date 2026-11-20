--liquibase formatted sql

--changeset lockate:api-key-label-expiry
ALTER TABLE api_key ADD COLUMN label VARCHAR(60);
ALTER TABLE api_key ADD COLUMN expires_at TIMESTAMP(6) WITHOUT TIME ZONE;
