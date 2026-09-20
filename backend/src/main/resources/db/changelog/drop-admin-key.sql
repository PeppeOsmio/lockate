--liquibase formatted sql

--changeset lockate:drop-admin-key
DROP TABLE admin_key;
