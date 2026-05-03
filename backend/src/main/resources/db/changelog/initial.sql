-- liquibase formatted sql

-- changeset peppeosmio:1771240865391-1 splitStatements:false
CREATE TABLE ag_member
(
    id                 UUID         NOT NULL,
    anonymous_group_id UUID         NOT NULL,
    created_at         TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    is_admin           BOOLEAN      NOT NULL,
    name_cipher        BYTEA        NOT NULL,
    name_iv            BYTEA        NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    last_location_id   UUID,
    CONSTRAINT "ag_memberPK" PRIMARY KEY (id)
);

-- changeset peppeosmio:1771240865391-2 splitStatements:false
CREATE TABLE ag_member_location
(
    id                 UUID  NOT NULL,
    ag_member_id       UUID  NOT NULL,
    coordinates_cipher BYTEA NOT NULL,
    coordinates_iv     BYTEA NOT NULL,
    timestamp          TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT "ag_member_locationPK" PRIMARY KEY (id)
);

-- changeset peppeosmio:1771240865391-3 splitStatements:false
CREATE TABLE anonymous_group
(
    id                           UUID  NOT NULL,
    created_at                   TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    key_salt                     BYTEA NOT NULL,
    member_password_srp_salt     BYTEA NOT NULL,
    member_password_srp_verifier BYTEA NOT NULL,
    name_cipher                  BYTEA NOT NULL,
    name_iv                      BYTEA NOT NULL,
    CONSTRAINT "anonymous_groupPK" PRIMARY KEY (id)
);

-- changeset peppeosmio:1771240865391-4 splitStatements:false
CREATE TABLE api_key
(
    key            UUID NOT NULL,
    created_at     TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    last_validated TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT "api_keyPK" PRIMARY KEY (key)
);

-- changeset peppeosmio:1771240865391-5 splitStatements:false
ALTER TABLE ag_member
    ADD CONSTRAINT UC_AG_MEMBERLAST_LOCATION_ID_COL UNIQUE (last_location_id);

-- changeset peppeosmio:1771240865391-6 splitStatements:false
CREATE INDEX idx_ag_location_ag_member_id ON ag_member_location (ag_member_id);

-- changeset peppeosmio:1771240865391-7 splitStatements:false
CREATE INDEX idx_ag_member_ag_id ON ag_member (anonymous_group_id);

-- changeset peppeosmio:1771240865391-8 splitStatements:false
CREATE INDEX idx_location_timestamp ON ag_member_location (timestamp);

-- changeset peppeosmio:1771240865391-9 splitStatements:false
ALTER TABLE ag_member
    ADD CONSTRAINT "FK66nfe60hnv8acylrfmh34xaxs" FOREIGN KEY (last_location_id)
        REFERENCES ag_member_location (id) ON DELETE SET NULL;

-- changeset peppeosmio:1771240865391-10 splitStatements:false
ALTER TABLE ag_member_location
    ADD CONSTRAINT "FKrqqul8il5e6ojn34jdef0p27f" FOREIGN KEY (ag_member_id)
        REFERENCES ag_member (id) ON DELETE CASCADE;

-- changeset peppeosmio:1771240865391-11 splitStatements:false
ALTER TABLE ag_member
    ADD CONSTRAINT "FKshcr3ffwyr1yxa0ccu6jf5i0o" FOREIGN KEY (anonymous_group_id)
        REFERENCES anonymous_group (id) ON DELETE CASCADE;

