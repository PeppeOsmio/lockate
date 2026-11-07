--liquibase formatted sql

--changeset lockate:circle-fields
-- A stored circle can't be split into centre, radius and name without the group key
DELETE FROM ag_circle;
ALTER TABLE ag_circle DROP COLUMN data_cipher;
ALTER TABLE ag_circle DROP COLUMN data_iv;
ALTER TABLE ag_circle ADD COLUMN coordinates_cipher BYTEA NOT NULL;
ALTER TABLE ag_circle ADD COLUMN coordinates_iv BYTEA NOT NULL;
ALTER TABLE ag_circle ADD COLUMN radius_cipher BYTEA NOT NULL;
ALTER TABLE ag_circle ADD COLUMN radius_iv BYTEA NOT NULL;
ALTER TABLE ag_circle ADD COLUMN name_cipher BYTEA NOT NULL;
ALTER TABLE ag_circle ADD COLUMN name_iv BYTEA NOT NULL;
