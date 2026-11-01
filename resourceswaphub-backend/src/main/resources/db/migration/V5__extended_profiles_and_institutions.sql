-- V5: Extended user profiles, education/employment, skills/interests, institution directory
-- All timestamps are UTC. UUIDs use PostgreSQL's built-in gen_random_uuid().

-- pg_trgm must be installed before idx_institutions_name_trgm below references gin_trgm_ops.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Extend users table with additional profile fields
ALTER TABLE users 
    ADD COLUMN bio VARCHAR(500),
    ADD COLUMN state VARCHAR(80),
    ADD COLUMN country VARCHAR(80) DEFAULT 'India',
    ADD COLUMN preferred_language VARCHAR(10) DEFAULT 'en',
    ADD COLUMN profile_visibility VARCHAR(20) DEFAULT 'PUBLIC',
    ADD COLUMN user_type VARCHAR(30),
    ADD COLUMN profile_completed_extended BOOLEAN DEFAULT FALSE,
    ADD COLUMN school_name VARCHAR(200),
    ADD COLUMN school_location_state VARCHAR(80),
    ADD COLUMN school_location_city VARCHAR(80),
    ADD COLUMN school_location_district VARCHAR(80),
    ADD COLUMN school_class VARCHAR(20),
    ADD COLUMN school_stream VARCHAR(50),
    ADD COLUMN school_board VARCHAR(30),
    ADD COLUMN school_academic_year VARCHAR(20),
    ADD COLUMN school_expected_graduation_year INTEGER,
    ADD COLUMN college_name VARCHAR(200),
    ADD COLUMN college_institution_type VARCHAR(50),
    ADD COLUMN college_city VARCHAR(80),
    ADD COLUMN college_state VARCHAR(80),
    ADD COLUMN college_course VARCHAR(100),
    ADD COLUMN college_branch VARCHAR(100),
    ADD COLUMN college_department VARCHAR(100),
    ADD COLUMN college_current_year_semester VARCHAR(30),
    ADD COLUMN college_admission_year INTEGER,
    ADD COLUMN college_expected_graduation_year INTEGER,
    ADD COLUMN professional_designation VARCHAR(120),
    ADD COLUMN professional_company VARCHAR(150),
    ADD COLUMN professional_industry VARCHAR(100),
    ADD COLUMN professional_work_location VARCHAR(150),
    ADD COLUMN professional_years_experience INTEGER,
    ADD COLUMN professional_highest_qualification VARCHAR(100),
    ADD COLUMN professional_college_attended VARCHAR(200),
    ADD COLUMN teacher_institution_name VARCHAR(200),
    ADD COLUMN teacher_institution_type VARCHAR(50),
    ADD COLUMN teacher_subjects TEXT,
    ADD COLUMN teacher_classes_taught TEXT,
    ADD COLUMN teacher_years_experience INTEGER,
    ADD COLUMN teacher_areas_expertise TEXT,
    ADD COLUMN teacher_can_help_with TEXT,
    ADD COLUMN other_occupation VARCHAR(150),
    ADD COLUMN other_learning_goals TEXT,
    ADD COLUMN other_can_contribute TEXT;

-- Constraints for visibility and user types
ALTER TABLE users ADD CONSTRAINT ck_users_profile_visibility 
    CHECK (profile_visibility IN ('PUBLIC', 'PRIVATE', 'CONNECTIONS_ONLY'));
ALTER TABLE users ADD CONSTRAINT ck_users_user_type 
    CHECK (user_type IN ('SCHOOL_STUDENT', 'COLLEGE_STUDENT', 'WORKING_PROFESSIONAL', 'TEACHER_EDUCATOR', 'OTHER'));
ALTER TABLE users ADD CONSTRAINT ck_users_school_board 
    CHECK (school_board IN ('CBSE', 'ICSE', 'STATE_BOARD', 'IB', 'CAMBRIDGE', 'OTHER'));

-- Institution directory
CREATE TABLE institutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(300) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL,
    institution_type VARCHAR(50) NOT NULL,
    official_code VARCHAR(100),
    official_code_type VARCHAR(30),
    state VARCHAR(80),
    district VARCHAR(80),
    city VARCHAR(80),
    source VARCHAR(50) NOT NULL,
    source_url VARCHAR(500),
    source_last_updated TIMESTAMPTZ,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_institutions_type CHECK (institution_type IN ('SCHOOL', 'COLLEGE', 'UNIVERSITY', 'AUTONOMOUS_COLLEGE', 'AFFILIATED_COLLEGE', 'CONSTITUENT_COLLEGE', 'STANDALONE', 'OTHER')),
    CONSTRAINT ck_institutions_official_code_type CHECK (official_code_type IN ('UDISE', 'AISHE', 'AICTE', 'OTHER')),
    CONSTRAINT ck_institutions_verification_status CHECK (verification_status IN ('VERIFIED', 'PENDING', 'UNVERIFIED', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_institutions_official_code ON institutions (official_code_type, official_code) WHERE official_code IS NOT NULL;
CREATE INDEX idx_institutions_normalized_name ON institutions (normalized_name);
CREATE INDEX idx_institutions_name_trgm ON institutions USING gin (name gin_trgm_ops);
CREATE INDEX idx_institutions_state_city ON institutions (state, city);
CREATE INDEX idx_institutions_type ON institutions (institution_type);

CREATE TRIGGER trg_institutions_updated_at
    BEFORE UPDATE ON institutions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Institution aliases
CREATE TABLE institution_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    alias_name VARCHAR(300) NOT NULL,
    normalized_alias VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_institution_aliases_institution ON institution_aliases (institution_id);
CREATE INDEX idx_institution_aliases_normalized ON institution_aliases (normalized_alias);

-- User education records
CREATE TABLE user_education (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    institution_id UUID REFERENCES institutions(id),
    institution_name_manual VARCHAR(200),
    degree VARCHAR(100),
    branch_specialization VARCHAR(100),
    department VARCHAR(100),
    start_year INTEGER,
    end_year INTEGER,
    current BOOLEAN DEFAULT FALSE,
    grade_percentage DECIMAL(5,2),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_education_user ON user_education (user_id);
CREATE TRIGGER trg_user_education_updated_at
    BEFORE UPDATE ON user_education
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- User employment records
CREATE TABLE user_employment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(150),
    designation VARCHAR(120) NOT NULL,
    industry VARCHAR(100),
    start_date DATE,
    end_date DATE,
    current BOOLEAN DEFAULT FALSE,
    description TEXT,
    skills_used TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_employment_user ON user_employment (user_id);
CREATE TRIGGER trg_user_employment_updated_at
    BEFORE UPDATE ON user_employment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Skills
CREATE TABLE skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    normalized_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skills_normalized ON skills (normalized_name);

-- User skills
CREATE TABLE user_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    proficiency_level VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_skills UNIQUE (user_id, skill_id),
    CONSTRAINT ck_user_skills_proficiency CHECK (proficiency_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);

CREATE INDEX idx_user_skills_user ON user_skills (user_id);
CREATE INDEX idx_user_skills_skill ON user_skills (skill_id);

-- Subjects
CREATE TABLE subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    normalized_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subjects_normalized ON subjects (normalized_name);

-- User subjects (can help with / want help with)
CREATE TABLE user_subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    relationship_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_subjects UNIQUE (user_id, subject_id, relationship_type),
    CONSTRAINT ck_user_subjects_type CHECK (relationship_type IN ('CAN_HELP', 'WANT_HELP', 'INTERESTED'))
);

CREATE INDEX idx_user_subjects_user ON user_subjects (user_id);
CREATE INDEX idx_user_subjects_subject ON user_subjects (subject_id);

-- User interests
CREATE TABLE user_interests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    interest VARCHAR(150) NOT NULL,
    normalized_interest VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_interests UNIQUE (user_id, normalized_interest)
);

CREATE INDEX idx_user_interests_user ON user_interests (user_id);

-- Institution suggestions (for manual entries)
CREATE TABLE institution_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggested_by UUID REFERENCES users(id),
    name VARCHAR(300) NOT NULL,
    institution_type VARCHAR(50) NOT NULL,
    state VARCHAR(80),
    city VARCHAR(80),
    district VARCHAR(80),
    additional_info TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_institution_suggestions_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'MERGED'))
);

CREATE INDEX idx_institution_suggestions_status ON institution_suggestions (status);
