CREATE TABLE users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username         VARCHAR(100) NOT NULL UNIQUE,
    password_hash    VARCHAR(200) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    candidate_ref_id BIGINT
);

CREATE TABLE job_descriptions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    location          VARCHAR(120),
    experience_years  INT          NOT NULL DEFAULT 0,
    education         VARCHAR(300),
    must_have         TEXT,
    nice_to_have      TEXT,
    resume_weight     DOUBLE NOT NULL DEFAULT 0.6,
    qa_weight         DOUBLE NOT NULL DEFAULT 0.4,
    pass_threshold    DOUBLE NOT NULL DEFAULT 65.0,
    confidence_cutoff DOUBLE NOT NULL DEFAULT 0.6,
    summary           TEXT
);

CREATE TABLE candidates (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT,
    name             VARCHAR(120) NOT NULL,
    email            VARCHAR(160),
    applied_jd       BIGINT,
    experience_years INT DEFAULT 0,
    education        VARCHAR(200),
    location         VARCHAR(120),
    profile_type     VARCHAR(60),
    resume           TEXT,
    status           VARCHAR(30) NOT NULL DEFAULT 'APPLIED'
);

CREATE TABLE questions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jd_id             BIGINT NOT NULL,
    text              TEXT   NOT NULL,
    reference_answer  TEXT,
    rubric            TEXT,
    time_limit_seconds INT   NOT NULL DEFAULT 300,
    order_index       INT    NOT NULL DEFAULT 0
);

CREATE TABLE applications (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id      BIGINT NOT NULL,
    jd_id             BIGINT NOT NULL,
    resume_score      DOUBLE,
    resume_confidence DOUBLE,
    qa_score          DOUBLE,
    combined_score    DOUBLE,
    band              VARCHAR(10),
    status            VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    next_steps        VARCHAR(200),
    resume_matched    TEXT,
    resume_gaps       TEXT,
    resume_summary    TEXT,
    CONSTRAINT uq_application UNIQUE (candidate_id, jd_id)
);

CREATE TABLE answers (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    question_id    BIGINT NOT NULL,
    answer_text    TEXT   NOT NULL,
    score          DOUBLE,
    confidence     DOUBLE,
    timeout_submit BOOLEAN NOT NULL DEFAULT FALSE,
    time_taken_s   INT,
    submitted_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_answer UNIQUE (application_id, question_id)
);

CREATE TABLE answer_rubric_hits (
    answer_id  BIGINT NOT NULL,
    rubric_hit VARCHAR(120)
);

CREATE TABLE interviews (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    interviewer_id BIGINT NOT NULL,
    scheduled_at   TIMESTAMP NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    decision       VARCHAR(20)
);

CREATE TABLE notes (
    interview_id BIGINT NOT NULL,
    author_role  VARCHAR(20),
    text         TEXT,
    created_at   TIMESTAMP,
    note_order   INT
);

CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT,
    actor_role  VARCHAR(20) NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id   BIGINT,
    details     TEXT,
    created_at  TIMESTAMP   NOT NULL
);

CREATE TABLE anticheat_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    event_type     VARCHAR(30) NOT NULL,
    payload        TEXT,
    occurred_at    TIMESTAMP NOT NULL
);
