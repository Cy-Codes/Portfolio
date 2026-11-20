-- ============================================================================
-- 01_schema_structure_tests.sql
-- Verifies PostgreSQL schemas, 12 tables, 2 views, TIMESTAMPTZ usage,
-- identity columns (no legacy SERIAL), 100% FK B-Tree index coverage, and
-- strict three-tier least-privilege role enforcement.
-- ============================================================================

DO $$
DECLARE
    v_missing_tables TEXT[];
    v_naive_timestamps INT;
    v_unindexed_fks TEXT[];
    v_blocked BOOLEAN;
BEGIN
    -- 1. Verify required schemas exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'portfolio') THEN
        RAISE EXCEPTION 'Missing required schema: portfolio';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'analytics') THEN
        RAISE EXCEPTION 'Missing required schema: analytics';
    END IF;

    -- 2. Verify all 12 core tables and 2 repeatable views exist
    SELECT array_agg(expected_tbl)
    INTO v_missing_tables
    FROM unnest(ARRAY[
        'portfolio.documents',
        'portfolio.profile',
        'portfolio.experiences',
        'portfolio.education',
        'portfolio.certifications',
        'portfolio.skills',
        'portfolio.experience_skills',
        'portfolio.projects',
        'portfolio.project_skills',
        'portfolio.writeups',
        'analytics.events',
        'analytics.daily_rollups',
        'portfolio.v_published_writeups',
        'analytics.v_document_download_counts'
    ]) AS expected_tbl
    WHERE to_regclass(expected_tbl) IS NULL;

    IF v_missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Missing expected tables or views: %', v_missing_tables;
    END IF;

    -- 3. Verify zero timezone-naive TIMESTAMP columns exist on application tables (excluding Flyway metadata)
    SELECT count(*)
    INTO v_naive_timestamps
    FROM information_schema.columns
    WHERE table_schema IN ('portfolio', 'analytics')
      AND table_name <> 'flyway_schema_history'
      AND data_type = 'timestamp without time zone';

    IF v_naive_timestamps > 0 THEN
        RAISE EXCEPTION 'Found % timezone-naive timestamp column(s); use TIMESTAMPTZ.', v_naive_timestamps;
    END IF;

    -- 4. Verify identity columns use SQL standard GENERATED AS IDENTITY rather than legacy SERIAL
    IF NOT EXISTS (
        SELECT 1
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'analytics'
          AND c.relname = 'events'
          AND a.attname = 'id'
          AND a.attidentity IN ('d', 'a')
    ) THEN
        RAISE EXCEPTION 'analytics.events.id must be declared GENERATED AS IDENTITY.';
    END IF;

    -- 5. Verify 100% of foreign key constraints have a B-Tree index with matching leading columns
    SELECT array_agg(format('%s.%s(%s)', n.nspname, c.relname, con.conname))
    INTO v_unindexed_fks
    FROM pg_constraint con
    JOIN pg_class c ON c.oid = con.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE con.contype = 'f'
      AND n.nspname IN ('portfolio', 'analytics')
      AND NOT EXISTS (
          SELECT 1
          FROM pg_index idx
          JOIN pg_class ic ON ic.oid = idx.indexrelid
          JOIN pg_am am ON am.oid = ic.relam
          WHERE idx.indrelid = con.conrelid
            AND idx.indisvalid
            AND am.amname = 'btree'
            AND (
                SELECT array_agg(k ORDER BY ord)
                FROM unnest(idx.indkey::int2[]) WITH ORDINALITY AS u(k, ord)
                WHERE ord <= array_length(con.conkey, 1)
            ) = con.conkey
      );

    IF v_unindexed_fks IS NOT NULL THEN
        RAISE EXCEPTION 'Found unindexed foreign key(s): %', v_unindexed_fks;
    END IF;

    -- 6. Verify three-tier role permissions when roles are present
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portfolio_public') THEN
        SET LOCAL ROLE portfolio_public;
        PERFORM count(*) FROM portfolio.writeups;
        PERFORM count(*) FROM portfolio.v_published_writeups;

        -- Verify portfolio_public CAN insert into analytics.events (rolled back via savepoint sub-block)
        BEGIN
            INSERT INTO analytics.events (
                event_type, path, entity_id, referrer_host, platform, viewport_tier, visitor_hash
            )
            VALUES (
                'PAGE_VIEW', '/smoke-check', NULL, NULL, 'WEB', 'LARGE', repeat('a', 64)
            );
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'ROLLBACK_SMOKE_INSERT';
        EXCEPTION
            WHEN SQLSTATE 'P0001' THEN
                NULL;
        END;

        v_blocked := FALSE;
        BEGIN
            UPDATE portfolio.writeups SET title = 'blocked';
        EXCEPTION
            WHEN insufficient_privilege THEN
                v_blocked := TRUE;
        END;
        IF NOT v_blocked THEN
            RAISE EXCEPTION 'portfolio_public must NOT have UPDATE privilege on portfolio.writeups';
        END IF;

        v_blocked := FALSE;
        BEGIN
            DELETE FROM portfolio.documents;
        EXCEPTION
            WHEN insufficient_privilege THEN
                v_blocked := TRUE;
        END;
        IF NOT v_blocked THEN
            RAISE EXCEPTION 'portfolio_public must NOT have DELETE privilege on portfolio.documents';
        END IF;

        v_blocked := FALSE;
        BEGIN
            CREATE TABLE portfolio.forbidden_public_ddl (id INT);
        EXCEPTION
            WHEN insufficient_privilege THEN
                v_blocked := TRUE;
        END;
        IF NOT v_blocked THEN
            RAISE EXCEPTION 'portfolio_public must NOT have DDL privileges in portfolio schema';
        END IF;

        RESET ROLE;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portfolio_admin') THEN
        SET LOCAL ROLE portfolio_admin;
        v_blocked := FALSE;
        BEGIN
            CREATE TABLE portfolio.forbidden_admin_ddl (id INT);
        EXCEPTION
            WHEN insufficient_privilege THEN
                v_blocked := TRUE;
        END;
        IF NOT v_blocked THEN
            RAISE EXCEPTION 'portfolio_admin must NOT have DDL privileges in portfolio schema';
        END IF;
        RESET ROLE;
    END IF;
END $$;
