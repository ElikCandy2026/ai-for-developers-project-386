CREATE TABLE IF NOT EXISTS meeting_types (
    id               TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    description      TEXT NOT NULL DEFAULT '',
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    created_at_ms    INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meeting_types_created
    ON meeting_types (created_at_ms);

CREATE TABLE IF NOT EXISTS bookings (
    id               TEXT PRIMARY KEY,
    meeting_type_id  TEXT NOT NULL REFERENCES meeting_types (id),
    guest_name       TEXT NOT NULL,
    guest_email      TEXT NOT NULL,
    notes            TEXT,
    start_ms         INTEGER NOT NULL,
    end_ms           INTEGER NOT NULL,
    created_at_ms    INTEGER NOT NULL,
    CHECK (end_ms > start_ms)
);

CREATE INDEX IF NOT EXISTS idx_bookings_start
    ON bookings (start_ms);

CREATE INDEX IF NOT EXISTS idx_bookings_interval
    ON bookings (start_ms, end_ms);
