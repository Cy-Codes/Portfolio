-- ============================================================================
-- R__portfolio_read_views.sql
-- Repeatable views and pure SQL functions maintained idempotently by Flyway.
-- ============================================================================

CREATE OR REPLACE FUNCTION portfolio.fn_estimate_reading_time_min(p_markdown TEXT)
RETURNS INT
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT GREATEST(
        1,
        CEIL(
            COALESCE(
                array_length(regexp_split_to_array(NULLIF(trim(p_markdown), ''), '\s+'), 1),
                1
            ) / 200.0
        )::INT
    );
$$;

CREATE OR REPLACE VIEW portfolio.v_published_writeups AS
SELECT
    id,
    slug,
    title,
    subtitle,
    summary,
    body_markdown,
    tags,
    status,
    reading_time_min,
    cover_document_id,
    search_vector,
    published_at,
    created_at,
    updated_at
FROM portfolio.writeups
WHERE status = 'PUBLISHED';

CREATE OR REPLACE VIEW analytics.v_document_download_counts AS
SELECT
    entity_id AS document_id,
    COUNT(*)::BIGINT AS download_count
FROM analytics.events
WHERE event_type = 'DOCUMENT_DOWNLOAD'
  AND entity_id IS NOT NULL
GROUP BY entity_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portfolio_admin') THEN
        GRANT SELECT ON portfolio.v_published_writeups TO portfolio_admin;
        GRANT SELECT ON analytics.v_document_download_counts TO portfolio_admin;
        GRANT EXECUTE ON FUNCTION portfolio.fn_estimate_reading_time_min(TEXT) TO portfolio_admin;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'portfolio_public') THEN
        GRANT SELECT ON portfolio.v_published_writeups TO portfolio_public;
        GRANT SELECT ON analytics.v_document_download_counts TO portfolio_public;
    END IF;
END $$;
