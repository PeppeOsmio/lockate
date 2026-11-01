--liquibase formatted sql

--changeset lockate:circles
-- Stored locations are now padded, AAD-bound payloads that the old clients can't produce or read
UPDATE ag_member SET last_location_id = NULL;
DELETE FROM ag_member_location;
ALTER TABLE ag_member_location RENAME COLUMN coordinates_cipher TO payload_cipher;
ALTER TABLE ag_member_location RENAME COLUMN coordinates_iv TO payload_iv;

CREATE TABLE ag_circle
(
    id                 UUID                        NOT NULL,
    anonymous_group_id UUID                        NOT NULL,
    data_cipher        BYTEA                       NOT NULL,
    data_iv            BYTEA                       NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT "ag_circlePK" PRIMARY KEY (id),
    CONSTRAINT fk_ag_circle_group FOREIGN KEY (anonymous_group_id)
        REFERENCES anonymous_group (id) ON DELETE CASCADE
);

CREATE INDEX idx_ag_circle_group_id ON ag_circle (anonymous_group_id);
