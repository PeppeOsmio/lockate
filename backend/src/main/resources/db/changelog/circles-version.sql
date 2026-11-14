--liquibase formatted sql

--changeset lockate:circles-version
ALTER TABLE anonymous_group ADD COLUMN circles_version BIGINT NOT NULL DEFAULT 0;
