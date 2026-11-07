--liquibase formatted sql

--changeset lockate:member-message-fields
-- A stored payload can't be split into coordinates and activity without the group key
UPDATE ag_member SET last_location_id = NULL;
DELETE FROM ag_member_location;
ALTER TABLE ag_member_location RENAME COLUMN payload_cipher TO coordinates_cipher;
ALTER TABLE ag_member_location RENAME COLUMN payload_iv TO coordinates_iv;
ALTER TABLE ag_member_location ADD COLUMN activity_cipher BYTEA NOT NULL;
ALTER TABLE ag_member_location ADD COLUMN activity_iv BYTEA NOT NULL;
