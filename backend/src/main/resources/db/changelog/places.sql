--liquibase formatted sql

--changeset lockate:places
-- Circles are called places everywhere; the rows and their ciphertexts stay as they are
ALTER TABLE ag_circle RENAME TO ag_place;
ALTER TABLE ag_place RENAME CONSTRAINT "ag_circlePK" TO "ag_placePK";
ALTER TABLE ag_place RENAME CONSTRAINT fk_ag_circle_group TO fk_ag_place_group;
ALTER INDEX idx_ag_circle_group_id RENAME TO idx_ag_place_group_id;
ALTER TABLE anonymous_group RENAME COLUMN circles_version TO places_version;
