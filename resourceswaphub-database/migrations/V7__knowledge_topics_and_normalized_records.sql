-- V7: Normalized education/employment records, a unified knowledge-topic taxonomy,
--     per-record visibility, and institution alias typing.
--
-- Design notes:
--   * Additive only. The V5 wide-column profile on `users` is preserved so the deployed
--     frontend keeps working; the normalized tables below layer alongside it.
--   * The V5 tables `skills`, `user_skills`, `subjects`, `user_subjects`, `user_interests`
--     were never wired to any entity/API. They are superseded by the unified `topics` /
--     `user_topics` model below and are intentionally left in place (not dropped) to keep
--     this migration non-destructive. See docs/PROFILE_SCHEMA.md.
--   * All timestamps are UTC. `set_updated_at()` is defined in V1; `pg_trgm` in V5.

-- ---------------------------------------------------------------------------
-- 1) Unified knowledge taxonomy (skills, subjects, technologies, interests,
--    expertise, learning goals, competitive-exam interests).
-- ---------------------------------------------------------------------------
CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    topic_type VARCHAR(30) NOT NULL,
    description VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_topics_normalized UNIQUE (normalized_name),
    CONSTRAINT ck_topics_type CHECK (topic_type IN
        ('SKILL', 'SUBJECT', 'TECHNOLOGY', 'INTEREST', 'EXPERTISE', 'LEARNING_GOAL', 'EXAM'))
);

CREATE INDEX idx_topics_type ON topics (topic_type);
CREATE INDEX idx_topics_normalized_trgm ON topics USING gin (normalized_name gin_trgm_ops);

-- Aliases / synonyms / abbreviations so "DS" can resolve to "Data Structures".
CREATE TABLE topic_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    alias VARCHAR(120) NOT NULL,
    normalized_alias VARCHAR(120) NOT NULL,
    alias_type VARCHAR(20) NOT NULL DEFAULT 'SYNONYM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_topic_aliases_type CHECK (alias_type IN ('SYNONYM', 'ABBREVIATION', 'FORMER', 'COMMON'))
);

CREATE UNIQUE INDEX uq_topic_aliases ON topic_aliases (topic_id, normalized_alias);
CREATE INDEX idx_topic_aliases_normalized ON topic_aliases (normalized_alias);

-- A user's relationship to a topic. Relation type is explicit so that an interest
-- is never silently treated as expertise (brief Part 3 §9).
CREATE TABLE user_topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    relation_type VARCHAR(20) NOT NULL,
    proficiency VARCHAR(20),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_topics UNIQUE (user_id, topic_id, relation_type),
    CONSTRAINT ck_user_topics_relation CHECK (relation_type IN
        ('INTERESTED_IN', 'LEARNING', 'CAN_HELP_WITH', 'EXPERIENCED_IN')),
    CONSTRAINT ck_user_topics_proficiency CHECK (proficiency IS NULL OR proficiency IN
        ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT ck_user_topics_visibility CHECK (visibility IN ('PRIVATE', 'PLATFORM_USERS', 'PUBLIC'))
);

CREATE INDEX idx_user_topics_user ON user_topics (user_id);
CREATE INDEX idx_user_topics_topic ON user_topics (topic_id);

CREATE TRIGGER trg_user_topics_updated_at
    BEFORE UPDATE ON user_topics
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 2) Extend education records toward the brief's model (Part 6 §14).
--    `current`, `degree`, `branch_specialization`, `department`, `start_year`,
--    `end_year`, `grade_percentage`, `description`, `institution_id`,
--    `institution_name_manual` already exist from V5.
-- ---------------------------------------------------------------------------
ALTER TABLE user_education
    ADD COLUMN education_level VARCHAR(30),
    ADD COLUMN course VARCHAR(120),
    ADD COLUMN school_board VARCHAR(30),
    ADD COLUMN grade VARCHAR(20),
    ADD COLUMN academic_stream VARCHAR(60),
    ADD COLUMN current_year VARCHAR(30),
    ADD COLUMN current_semester VARCHAR(30),
    ADD COLUMN expected_completion_year INTEGER,
    ADD COLUMN academic_session VARCHAR(20),
    ADD COLUMN education_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

ALTER TABLE user_education ADD CONSTRAINT ck_user_education_status CHECK (education_status IN
    ('CURRENT', 'COMPLETED', 'PAUSED', 'DISCONTINUED'));
ALTER TABLE user_education ADD CONSTRAINT ck_user_education_visibility CHECK (visibility IN
    ('PRIVATE', 'PLATFORM_USERS', 'PUBLIC'));

CREATE INDEX idx_user_education_institution ON user_education (institution_id);

-- ---------------------------------------------------------------------------
-- 3) Extend employment records toward the brief's model (Part 6 §15).
--    `company_name`, `designation`, `industry`, `start_date`, `end_date`,
--    `current`, `description`, `skills_used` already exist from V5.
-- ---------------------------------------------------------------------------
ALTER TABLE user_employment
    ADD COLUMN employment_type VARCHAR(30),
    ADD COLUMN work_city VARCHAR(80),
    ADD COLUMN work_state VARCHAR(80),
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

ALTER TABLE user_employment ADD CONSTRAINT ck_user_employment_type CHECK (employment_type IS NULL OR employment_type IN
    ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT', 'FREELANCE', 'SELF_EMPLOYED', 'OTHER'));
ALTER TABLE user_employment ADD CONSTRAINT ck_user_employment_visibility CHECK (visibility IN
    ('PRIVATE', 'PLATFORM_USERS', 'PUBLIC'));

-- ---------------------------------------------------------------------------
-- 4) Institution alias typing (brief Part 5 §13). `institution_aliases` exists from V5
--    with (alias_name, normalized_alias); add the alias type + a per-institution unique.
-- ---------------------------------------------------------------------------
ALTER TABLE institution_aliases ADD COLUMN alias_type VARCHAR(20) NOT NULL DEFAULT 'COMMON';
ALTER TABLE institution_aliases ADD CONSTRAINT ck_institution_aliases_type CHECK (alias_type IN
    ('ABBREVIATION', 'FORMER', 'ALTERNATE', 'COMMON'));

CREATE UNIQUE INDEX uq_institution_aliases ON institution_aliases (institution_id, normalized_alias);

-- Trigram index on the normalized name so alias/fuzzy institution search stays fast
-- (V5 only indexed the raw `name`). Supports LIKE '%..%' and similarity() ranking.
CREATE INDEX idx_institutions_normalized_name_trgm ON institutions USING gin (normalized_name gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- 5) Starter canonical topics so autocomplete is useful on day one.
--    Curated common reference data (not fabricated official records). Idempotent.
-- ---------------------------------------------------------------------------
INSERT INTO topics (canonical_name, normalized_name, topic_type)
SELECT
    v.canonical,
    btrim(lower(regexp_replace(v.canonical, '[^A-Za-z0-9 ]', '', 'g'))),
    v.topic_type
FROM (VALUES
    -- Subjects
    ('Mathematics', 'SUBJECT'),
    ('Physics', 'SUBJECT'),
    ('Chemistry', 'SUBJECT'),
    ('Biology', 'SUBJECT'),
    ('Computer Science', 'SUBJECT'),
    ('Economics', 'SUBJECT'),
    ('History', 'SUBJECT'),
    ('Geography', 'SUBJECT'),
    ('Political Science', 'SUBJECT'),
    ('English', 'SUBJECT'),
    ('Hindi', 'SUBJECT'),
    ('Accountancy', 'SUBJECT'),
    ('Business Studies', 'SUBJECT'),
    ('Statistics', 'SUBJECT'),
    -- Skills / technologies
    ('Java', 'TECHNOLOGY'),
    ('Spring Boot', 'TECHNOLOGY'),
    ('Python', 'TECHNOLOGY'),
    ('JavaScript', 'TECHNOLOGY'),
    ('TypeScript', 'TECHNOLOGY'),
    ('React', 'TECHNOLOGY'),
    ('Next.js', 'TECHNOLOGY'),
    ('Node.js', 'TECHNOLOGY'),
    ('PostgreSQL', 'TECHNOLOGY'),
    ('SQL', 'TECHNOLOGY'),
    ('Git', 'TECHNOLOGY'),
    ('Docker', 'TECHNOLOGY'),
    ('Data Structures', 'SKILL'),
    ('Algorithms', 'SKILL'),
    ('Machine Learning', 'SKILL'),
    ('Public Speaking', 'SKILL'),
    ('Graphic Design', 'SKILL'),
    ('Technical Writing', 'SKILL'),
    -- Interests
    ('Backend Development', 'INTEREST'),
    ('Frontend Development', 'INTEREST'),
    ('Artificial Intelligence', 'INTEREST'),
    ('Finance', 'INTEREST'),
    ('Robotics', 'INTEREST'),
    ('Entrepreneurship', 'INTEREST'),
    ('Open Source', 'INTEREST'),
    -- Learning goals
    ('Learn PostgreSQL', 'LEARNING_GOAL'),
    ('Understand Machine Learning', 'LEARNING_GOAL'),
    ('Improve Communication', 'LEARNING_GOAL'),
    -- Competitive exams
    ('JEE Main', 'EXAM'),
    ('JEE Advanced', 'EXAM'),
    ('NEET UG', 'EXAM'),
    ('CUET', 'EXAM'),
    ('UPSC CSE', 'EXAM'),
    ('GATE', 'EXAM')
) AS v(canonical, topic_type)
ON CONFLICT (normalized_name) DO NOTHING;

-- A few abbreviations so search resolves short forms to canonical topics.
INSERT INTO topic_aliases (topic_id, alias, normalized_alias, alias_type)
SELECT t.id, a.alias, btrim(lower(regexp_replace(a.alias, '[^A-Za-z0-9 ]', '', 'g'))), 'ABBREVIATION'
FROM (VALUES
    ('Data Structures', 'DS'),
    ('Algorithms', 'Algo'),
    ('Machine Learning', 'ML'),
    ('Artificial Intelligence', 'AI'),
    ('Computer Science', 'CS'),
    ('PostgreSQL', 'Postgres'),
    ('JavaScript', 'JS'),
    ('TypeScript', 'TS')
) AS a(canonical, alias)
JOIN topics t ON t.canonical_name = a.canonical
ON CONFLICT (topic_id, normalized_alias) DO NOTHING;
