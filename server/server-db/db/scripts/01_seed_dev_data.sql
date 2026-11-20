-- ============================================================================
-- 01_seed_dev_data.sql
-- Set-based, idempotent development seed script using generate_series (zero loops).
-- ============================================================================

BEGIN;

INSERT INTO portfolio.documents (
    id, title, category, original_filename, mime_type, size_bytes, sha256, storage_key, is_public
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001'::uuid,
        'Engineering Resume (2026)',
        'RESUME',
        'resume-2026.pdf',
        'application/pdf',
        184320,
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        'seed/resume-2026.pdf',
        TRUE
    ),
    (
        '10000000-0000-0000-0000-000000000002'::uuid,
        'KMP & Decompose Architecture Slides',
        'SLIDES',
        'kmp-decompose-slides.pdf',
        'application/pdf',
        942080,
        'd2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2',
        'seed/kmp-decompose-slides.pdf',
        TRUE
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.profile (
    id, full_name, headline, bio_markdown, location, availability, social_links, resume_document_id, updated_at
)
VALUES (
    1,
    'CY',
    'Staff Kotlin Multiplatform & Backend Systems Engineer',
    E'Building cross-platform products with **Kotlin Multiplatform**, **Compose**, **SwiftUI**, **Ktor**, and **PostgreSQL**.\n\nFocused on clean functional cores, resilient cloud infrastructure, and crisp multi-device UX.',
    'United States',
    'Open to selective advisory & architecture engagements',
    '[
      {"label":"GitHub","url":"https://github.com/cy","iconKey":"github"},
      {"label":"LinkedIn","url":"https://linkedin.com/in/cy","iconKey":"linkedin"}
    ]'::jsonb,
    '10000000-0000-0000-0000-000000000001'::uuid,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.skills (id, name, category, is_featured, sort_order)
VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, 'Kotlin Multiplatform', 'Mobile & KMP', TRUE, 1),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'Compose Multiplatform', 'UI Frameworks', TRUE, 2),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'SwiftUI', 'UI Frameworks', TRUE, 3),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'Ktor', 'Backend', TRUE, 4),
    ('20000000-0000-0000-0000-000000000005'::uuid, 'PostgreSQL', 'Databases', TRUE, 5),
    ('20000000-0000-0000-0000-000000000006'::uuid, 'Decompose & FlowMVI', 'Architecture', TRUE, 6),
    ('20000000-0000-0000-0000-000000000007'::uuid, 'Docker & Cloudflare', 'Infrastructure', FALSE, 7),
    ('20000000-0000-0000-0000-000000000008'::uuid, 'Kotlin/Wasm', 'Web', TRUE, 8)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.experiences (id, company, title, location, start_date, end_date, is_current, highlights, sort_order)
VALUES
    (
        '30000000-0000-0000-0000-000000000001'::uuid,
        'Cloud & Mobile Systems Lab',
        'Principal Platform Engineer',
        'Remote',
        '2024-03-01',
        NULL,
        TRUE,
        '["Architected shared KMP presentation layer powering Android, SwiftUI iOS, Desktop, and Wasm Web", "Split public and admin Ktor backends with least-privilege PostgreSQL roles"]'::jsonb,
        1
    ),
    (
        '30000000-0000-0000-0000-000000000002'::uuid,
        'Distributed Commerce Inc.',
        'Senior Backend & Mobile Engineer',
        'New York, NY',
        '2021-05-01',
        '2024-02-15',
        FALSE,
        '["Led PostgreSQL migration pipelines with Flyway and zero-downtime rollouts", "Reduced mobile crash rates by 42% using strict UDF/MVI state containers"]'::jsonb,
        2
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.experience_skills (experience_id, skill_id)
VALUES
    ('30000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid),
    ('30000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000004'::uuid),
    ('30000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000006'::uuid),
    ('30000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000005'::uuid)
ON CONFLICT (experience_id, skill_id) DO NOTHING;

INSERT INTO portfolio.education (id, institution, degree, field_of_study, start_date, end_date, sort_order)
VALUES
    (
        '40000000-0000-0000-0000-000000000001'::uuid,
        'State University',
        'B.S. Computer Science',
        'Distributed Systems & Compilers',
        '2017-09-01',
        '2021-05-15',
        1
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.certifications (id, name, issuer, issued_date, expires_date, credential_url, sort_order)
VALUES
    (
        '50000000-0000-0000-0000-000000000001'::uuid,
        'AWS Certified Solutions Architect',
        'Amazon Web Services',
        '2025-02-10',
        '2028-02-10',
        'https://aws.amazon.com/certification/',
        1
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.projects (id, slug, title, summary, case_study_markdown, repo_url, live_url, cover_document_id, status, is_featured, sort_order)
VALUES
    (
        '60000000-0000-0000-0000-000000000001'::uuid,
        'adaptive-kmp-portfolio',
        'Adaptive KMP Portfolio Platform',
        'Multiplatform portfolio targeting Android, Desktop, Wasm Web, and native SwiftUI iOS backed by split Ktor servers.',
        E'## Overview\nBuilt with Decompose + FlowMVI in pure KMP, Material 3 Adaptive breakpoints on Compose, SwiftUI on iOS, and compile-time separated Ktor JVM services.\n\n```kotlin\nval options = QueryOptions(page = 1, pageSize = 20)\n```',
        'https://github.com/cy/Portfolio',
        'https://cy.codes',
        NULL,
        'ACTIVE',
        TRUE,
        1
    ),
    (
        '60000000-0000-0000-0000-000000000002'::uuid,
        'pg-role-isolated-cms',
        'Three-Tier Role Isolated Postgres CMS',
        'Zero-trust public/admin database separation using Flyway one-shot migrations and Exposed 1.0 DSL.',
        E'## Security Boundary\nPublic traffic holds `SELECT` on published content and `INSERT`-only on cookieless analytics.',
        'https://github.com/cy/Portfolio',
        NULL,
        NULL,
        'ACTIVE',
        TRUE,
        2
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio.project_skills (project_id, skill_id)
VALUES
    ('60000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid),
    ('60000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000002'::uuid),
    ('60000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000003'::uuid),
    ('60000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000004'::uuid),
    ('60000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000005'::uuid)
ON CONFLICT (project_id, skill_id) DO NOTHING;

INSERT INTO portfolio.writeups (
    id, slug, title, subtitle, summary, body_markdown, tags, status, reading_time_min, published_at
)
VALUES
    (
        '70000000-0000-0000-0000-000000000001'::uuid,
        'decompose-flowmvi-with-swiftui-and-wasm',
        'Sharing Decompose + FlowMVI Across SwiftUI and Kotlin/Wasm',
        'Keeping presentation 100% Compose-free while embedding interactive Compose demo islands on iOS',
        'A practical blueprint for sharing MVI state and browser-synced routing across native SwiftUI and Compose Multiplatform.',
        E'# Why Compose-Free Presentation Matters\n\nSeparating `app/presentation` from `app/composeUi` lets SwiftUI drive native iOS navigation while reusing interactive Compose demos inline.\n\n```kotlin\nval state = store.states.collectAsState()\n```\n\n```demo:adaptive-breakpoints\n```\n\nResize the window or tap tiers inside the embedded demo to inspect `WindowSizeClass` transitions live.',
        ARRAY['kotlin-multiplatform', 'decompose', 'flowmvi', 'swiftui', 'wasm'],
        'PUBLISHED',
        6,
        NOW() - INTERVAL '7 days'
    ),
    (
        '70000000-0000-0000-0000-000000000002'::uuid,
        'three-tier-postgres-roles-in-ktor',
        'Three-Tier Least-Privilege PostgreSQL Roles in Ktor',
        'Separating migrator DDL, admin DML, and read-only public connections',
        'How compile-time split Ktor binaries pair with PostgreSQL role separation and pure GET semantics.',
        E'# Least Privilege at the Database Boundary\n\nBy moving download telemetry to `POST /api/v1/public/events`, `server-public` requires zero `UPDATE` permissions.\n\n```sql\nGRANT SELECT ON ALL TABLES IN SCHEMA portfolio TO portfolio_public;\nGRANT INSERT ON analytics.events TO portfolio_public;\n```',
        ARRAY['ktor', 'postgresql', 'security', 'flyway'],
        'PUBLISHED',
        5,
        NOW() - INTERVAL '2 days'
    ),
    (
        '70000000-0000-0000-0000-000000000003'::uuid,
        'cloudflare-r2-presigned-uploads-notes',
        'Cloudflare R2 Presigned Uploads Notes',
        'Draft engineering notes',
        'Work-in-progress notes covering magic-byte verification and zero-egress PDF delivery.',
        E'# Draft Notes\n\nPending benchmark figures.',
        ARRAY['cloudflare-r2', 'storage'],
        'DRAFT',
        2,
        NULL
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO analytics.events (
    event_type, path, entity_id, referrer_host, platform, viewport_tier, visitor_hash, created_at
)
SELECT
    CASE (s.day_offset % 4)
        WHEN 0 THEN 'PAGE_VIEW'
        WHEN 1 THEN 'WRITEUP_READ'
        WHEN 2 THEN 'PROJECT_CLICK'
        ELSE 'DOCUMENT_DOWNLOAD'
    END,
    CASE (s.day_offset % 4)
        WHEN 0 THEN '/'
        WHEN 1 THEN '/writeups/decompose-flowmvi-with-swiftui-and-wasm'
        WHEN 2 THEN '/projects/adaptive-kmp-portfolio'
        ELSE '/api/v1/public/documents/10000000-0000-0000-0000-000000000001/download'
    END,
    CASE (s.day_offset % 4)
        WHEN 1 THEN '70000000-0000-0000-0000-000000000001'::uuid
        WHEN 2 THEN '60000000-0000-0000-0000-000000000001'::uuid
        WHEN 3 THEN '10000000-0000-0000-0000-000000000001'::uuid
        ELSE NULL
    END,
    CASE (s.day_offset % 3)
        WHEN 0 THEN 'github.com'
        WHEN 1 THEN 'kotlinlang.org'
        ELSE NULL
    END,
    CASE (s.day_offset % 4)
        WHEN 0 THEN 'WEB'
        WHEN 1 THEN 'IOS'
        WHEN 2 THEN 'DESKTOP'
        ELSE 'ANDROID'
    END,
    CASE (s.day_offset % 5)
        WHEN 0 THEN 'COMPACT'
        WHEN 1 THEN 'MEDIUM'
        WHEN 2 THEN 'EXPANDED'
        WHEN 3 THEN 'LARGE'
        ELSE 'EXTRA_LARGE'
    END,
    repeat(to_hex(s.day_offset % 16), 64),
    NOW() - (s.day_offset || ' days')::interval
FROM generate_series(0, 29) AS s(day_offset)
WHERE NOT EXISTS (SELECT 1 FROM analytics.events LIMIT 1);

COMMIT;
