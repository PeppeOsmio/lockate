--liquibase formatted sql

--changeset lockate:sha256-member-tokens
-- BCrypt hashes can't be turned into SHA-256 ones without the tokens
DELETE FROM anonymous_group;
ALTER TABLE ag_member ALTER COLUMN token_hash TYPE BYTEA USING NULL;
