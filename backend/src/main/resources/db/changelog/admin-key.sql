-- liquibase formatted sql

-- changeset peppeosmio:1785059908276-1 splitStatements:false
CREATE TABLE admin_key
(
    id             UUID         NOT NULL,
    key_hash       VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    last_validated TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT "admin_keyPK" PRIMARY KEY (id)
);
