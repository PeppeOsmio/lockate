--liquibase formatted sql

--changeset lockate:api-key-id-secret
DROP TABLE api_key;
CREATE TABLE api_key
(
    id             UUID NOT NULL,
    secret_hash    VARCHAR(64) NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    last_validated TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT "api_keyPK" PRIMARY KEY (id)
);
