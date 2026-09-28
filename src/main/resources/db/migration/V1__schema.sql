CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    full_name     TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('USER', 'MANAGER', 'ADMIN')),
    manager_id    BIGINT NULL REFERENCES users (id)
);

CREATE TABLE access_types (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

CREATE TABLE access_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (id),
    access_type_id BIGINT NOT NULL REFERENCES access_types (id),
    reason         TEXT NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
    status         TEXT NOT NULL CHECK (status IN ('IN_PROGRESS', 'APPROVED', 'REJECTED')),
    current_step   TEXT NULL CHECK (current_step IN ('MANAGER', 'ADMIN')),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    -- A request waits on a step exactly while it is in progress.
    CONSTRAINT access_requests_step_matches_status
        CHECK ((status = 'IN_PROGRESS') = (current_step IS NOT NULL))
);

CREATE INDEX access_requests_user_id_idx ON access_requests (user_id);
CREATE INDEX access_requests_status_step_idx ON access_requests (status, current_step);

-- At most one pending or approved request per user and access. This backs up the
-- service's DUPLICATE_REQUEST check against two submissions racing each other.
CREATE UNIQUE INDEX access_requests_one_active_per_type
    ON access_requests (user_id, access_type_id)
    WHERE status <> 'REJECTED';

CREATE TABLE approvals (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id  BIGINT NOT NULL REFERENCES access_requests (id),
    step        TEXT NOT NULL CHECK (step IN ('MANAGER', 'ADMIN')),
    approver_id BIGINT NOT NULL REFERENCES users (id),
    decision    TEXT NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    comment     TEXT NULL CHECK (comment IS NULL OR char_length(comment) BETWEEN 1 AND 500),
    decided_at  TIMESTAMPTZ NOT NULL,
    -- Each step is decided once; a second decision on the same step fails here.
    CONSTRAINT approvals_request_step_key UNIQUE (request_id, step)
);
