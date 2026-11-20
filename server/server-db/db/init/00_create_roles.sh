#!/usr/bin/env bash
# ============================================================================
# PostgreSQL Init Hook: Provisions Least-Privilege Three-Tier Roles Idempotently
# Executed automatically by postgres image on fresh database initialization.
# ============================================================================
set -euo pipefail

readonly MIGRATOR_USER="${PORTFOLIO_MIGRATOR_USER:-portfolio_migrator}"
readonly MIGRATOR_PASSWORD="${PORTFOLIO_MIGRATOR_PASSWORD:-dev_migrator_change_me}"
readonly ADMIN_USER="${PORTFOLIO_ADMIN_USER:-portfolio_admin}"
readonly ADMIN_PASSWORD="${PORTFOLIO_ADMIN_PASSWORD:-dev_admin_change_me}"
readonly PUBLIC_USER="${PORTFOLIO_PUBLIC_USER:-portfolio_public}"
readonly PUBLIC_PASSWORD="${PORTFOLIO_PUBLIC_PASSWORD:-dev_public_change_me}"

psql -v ON_ERROR_STOP=1 \
    --username "${POSTGRES_USER}" \
    --dbname "${POSTGRES_DB}" \
    -v migrator_user="${MIGRATOR_USER}" \
    -v migrator_password="${MIGRATOR_PASSWORD}" \
    -v admin_user="${ADMIN_USER}" \
    -v admin_password="${ADMIN_PASSWORD}" \
    -v public_user="${PUBLIC_USER}" \
    -v public_password="${PUBLIC_PASSWORD}" <<'SQL'
SELECT set_config('portfolio.migrator_user', :'migrator_user', false);
SELECT set_config('portfolio.migrator_password', :'migrator_password', false);
SELECT set_config('portfolio.admin_user', :'admin_user', false);
SELECT set_config('portfolio.admin_password', :'admin_password', false);
SELECT set_config('portfolio.public_user', :'public_user', false);
SELECT set_config('portfolio.public_password', :'public_password', false);

DO $$
DECLARE
    v_migrator TEXT := current_setting('portfolio.migrator_user');
    v_migrator_pw TEXT := current_setting('portfolio.migrator_password');
    v_admin TEXT := current_setting('portfolio.admin_user');
    v_admin_pw TEXT := current_setting('portfolio.admin_password');
    v_public TEXT := current_setting('portfolio.public_user');
    v_public_pw TEXT := current_setting('portfolio.public_password');
    v_db TEXT := current_database();
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_migrator) THEN
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', v_migrator, v_migrator_pw);
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', v_migrator, v_migrator_pw);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_admin) THEN
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', v_admin, v_admin_pw);
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', v_admin, v_admin_pw);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_public) THEN
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L', v_public, v_public_pw);
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', v_public, v_public_pw);
    END IF;

    EXECUTE format('GRANT CONNECT, CREATE ON DATABASE %I TO %I', v_db, v_migrator);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', v_db, v_admin);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', v_db, v_public);
END $$;
SQL
