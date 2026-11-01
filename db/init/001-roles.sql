-- Phase 0: minimal roles so the app and Liquibase can connect.
-- Phase 1 tightens privileges (app_runtime must not own tables, FORCE RLS applies to it)
-- and documents the full rationale in docs/decisions.md.

CREATE ROLE app_migrator WITH LOGIN PASSWORD 'app_migrator' CREATEDB;
GRANT ALL PRIVILEGES ON DATABASE multitenant_crm TO app_migrator;
-- Postgres 15+ revokes CREATE on the public schema from PUBLIC by default.
GRANT ALL ON SCHEMA public TO app_migrator;

CREATE ROLE app_runtime WITH LOGIN PASSWORD 'app_runtime';
GRANT CONNECT ON DATABASE multitenant_crm TO app_runtime;
GRANT USAGE ON SCHEMA public TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;
