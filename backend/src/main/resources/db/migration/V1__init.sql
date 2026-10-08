CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ===================== ORG & USERS =====================
CREATE TABLE departments (
    code  VARCHAR(10)  PRIMARY KEY,
    name  VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(120) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT','FACULTY','HOD','ADMIN')),
    department_code  VARCHAR(10)  REFERENCES departments(code),
    roll_no          VARCHAR(20)  UNIQUE,
    mentor_id        UUID         REFERENCES users(id),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_roll_only_students  CHECK ((role = 'STUDENT') = (roll_no IS NOT NULL)),
    CONSTRAINT chk_students_have_mentor CHECK (role <> 'STUDENT' OR mentor_id IS NOT NULL),
    CONSTRAINT chk_dept_unless_admin   CHECK ((role = 'ADMIN') = (department_code IS NULL)),
    CONSTRAINT chk_not_own_mentor      CHECK (mentor_id IS NULL OR mentor_id <> id)
);
CREATE INDEX idx_users_mentor ON users(mentor_id);
CREATE INDEX idx_users_dept_role ON users(department_code, role);
CREATE UNIQUE INDEX uq_one_active_hod_per_dept ON users(department_code) WHERE role = 'HOD' AND active;

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id    UUID        NOT NULL,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID        REFERENCES refresh_tokens(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

-- ===================== ACADEMICS =====================
CREATE TABLE courses (
    code             VARCHAR(10)  PRIMARY KEY,
    title            VARCHAR(160) NOT NULL,
    department_code  VARCHAR(10)  NOT NULL REFERENCES departments(code),
    faculty_id       UUID         NOT NULL REFERENCES users(id),
    credits          SMALLINT     NOT NULL CHECK (credits BETWEEN 1 AND 6),
    semester         VARCHAR(20)  NOT NULL,
    classes_held     INT          NOT NULL DEFAULT 0 CHECK (classes_held >= 0)
);
CREATE INDEX idx_courses_faculty ON courses(faculty_id);
CREATE INDEX idx_courses_dept    ON courses(department_code);

CREATE TABLE enrollments (
    id                   BIGSERIAL PRIMARY KEY,
    student_id           UUID        NOT NULL REFERENCES users(id),
    course_code          VARCHAR(10) NOT NULL REFERENCES courses(code),
    classes_attended     INT         NOT NULL DEFAULT 0 CHECK (classes_attended >= 0),
    duty_leave_credited  INT         NOT NULL DEFAULT 0 CHECK (duty_leave_credited >= 0),
    grade                VARCHAR(2)  CHECK (grade IN ('A+','A','B+','B','C+','C','D','F')),
    grade_published      BOOLEAN     NOT NULL DEFAULT FALSE,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_enrollment UNIQUE (student_id, course_code),
    CONSTRAINT chk_published_has_grade CHECK (NOT grade_published OR grade IS NOT NULL)
);
CREATE INDEX idx_enrollments_course ON enrollments(course_code);

CREATE TABLE grade_changes (
    id             BIGSERIAL PRIMARY KEY,
    enrollment_id  BIGINT       NOT NULL REFERENCES enrollments(id),
    old_grade      VARCHAR(2)   NOT NULL,
    new_grade      VARCHAR(2)   NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    requested_by   UUID         NOT NULL REFERENCES users(id),
    approved_by    UUID         NOT NULL REFERENCES users(id),
    action_request_id UUID      NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_grade_change_differs CHECK (old_grade <> new_grade),
    CONSTRAINT chk_grade_change_two_people CHECK (approved_by <> requested_by)
);

-- ===================== DUTY LEAVE =====================
CREATE TABLE duty_leave_requests (
    id              BIGSERIAL PRIMARY KEY,
    student_id      UUID          NOT NULL REFERENCES users(id),
    start_date      DATE          NOT NULL,
    end_date        DATE          NOT NULL,
    working_days    INT           NOT NULL CHECK (working_days BETWEEN 1 AND 10),
    event_name      VARCHAR(120)  NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    reason_flagged  BOOLEAN       NOT NULL DEFAULT FALSE,
    requires_hod    BOOLEAN       NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    decided_by      UUID          REFERENCES users(id),
    decision_note   VARCHAR(500),
    decided_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_dl_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_dl_requires_hod CHECK (requires_hod = (working_days > 3)),
    CONSTRAINT chk_dl_not_self_decided CHECK (decided_by IS NULL OR decided_by <> student_id),
    CONSTRAINT no_overlapping_duty_leave EXCLUDE USING gist (
        student_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status IN ('PENDING','APPROVED'))
);
CREATE INDEX idx_dl_student_status ON duty_leave_requests(student_id, status);
CREATE INDEX idx_dl_status ON duty_leave_requests(status, created_at DESC);

-- ===================== CHAT =====================
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL DEFAULT 'New conversation',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user ON conversations(user_id, updated_at DESC);

CREATE TABLE messages (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content          TEXT        NOT NULL,
    tool_events      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    flagged          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation ON messages(conversation_id, id);

-- ===================== APPROVALS =====================
CREATE TABLE action_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID         REFERENCES conversations(id) ON DELETE SET NULL,
    requested_by     UUID         NOT NULL REFERENCES users(id),
    tool_name        VARCHAR(60)  NOT NULL CHECK (tool_name IN ('requestGradeChange','deactivateUser')),
    arguments        JSONB        NOT NULL,
    summary          VARCHAR(300) NOT NULL,
    required_role    VARCHAR(20)  NOT NULL CHECK (required_role IN ('HOD','ADMIN')),
    department_code  VARCHAR(10)  REFERENCES departments(code),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','EXECUTED','FAILED','REJECTED','EXPIRED')),
    reviewer_id      UUID         REFERENCES users(id),
    review_note      VARCHAR(500),
    failure_reason   VARCHAR(500),
    expires_at       TIMESTAMPTZ  NOT NULL,
    decided_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_no_self_review CHECK (reviewer_id IS NULL OR reviewer_id <> requested_by),
    CONSTRAINT chk_hod_routing CHECK (required_role <> 'HOD' OR department_code IS NOT NULL)
);
CREATE INDEX idx_action_status_expiry ON action_requests(status, expires_at);
CREATE INDEX idx_action_requester     ON action_requests(requested_by, created_at DESC);
CREATE UNIQUE INDEX uq_one_pending_grade_change ON action_requests ((arguments->>'enrollmentId'))
    WHERE tool_name = 'requestGradeChange' AND status = 'PENDING';
CREATE UNIQUE INDEX uq_one_pending_deactivation ON action_requests ((arguments->>'targetUserId'))
    WHERE tool_name = 'deactivateUser' AND status = 'PENDING';

-- ===================== AUDIT (append-only, hash-chained) =====================
CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID        REFERENCES users(id),
    event_type  VARCHAR(40) NOT NULL CHECK (event_type IN (
                  'LOGIN_SUCCESS','LOGIN_FAILURE','TOKEN_REUSE_DETECTED',
                  'TOOL_ALLOWED','TOOL_DENIED','TOOL_QUEUED','TOOL_FAILED',
                  'ACTION_EXECUTED','ACTION_FAILED','ACTION_REJECTED','ACTION_EXPIRED',
                  'DUTY_LEAVE_DECIDED','INJECTION_FLAGGED','INJECTION_BLOCKED','RATE_LIMITED')),
    tool_name   VARCHAR(60),
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    prev_hash   VARCHAR(64) NOT NULL,
    hash        VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_actor   ON audit_events(actor_id, id DESC);
CREATE INDEX idx_audit_type    ON audit_events(event_type, id DESC);
CREATE INDEX idx_audit_created ON audit_events(created_at DESC);

CREATE FUNCTION audit_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
