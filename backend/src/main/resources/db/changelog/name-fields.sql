--liquibase formatted sql

--changeset lockate:name-fields
-- Group and member names are now padded and bound to client-chosen ids, which the old ones aren't
DELETE FROM anonymous_group;
