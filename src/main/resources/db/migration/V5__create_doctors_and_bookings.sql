-- Q5 appointment booking. Slots are derived from configuration (app.booking), not stored;
-- a booking row claims one slot of one doctor.
CREATE TABLE doctors (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    specialty  VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE bookings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    doctor_id       BIGINT       NOT NULL REFERENCES doctors (id),
    -- The slot's start as an absolute instant; the API speaks clinic-local time (business zone).
    slot_start      TIMESTAMPTZ  NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL
        CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    hold_expires_at TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_bookings_doctor_id ON bookings (doctor_id);

-- The double-booking guarantee: at most one active (HELD or CONFIRMED) booking per doctor and slot.
-- Concurrent inserts for the same slot are serialized here; the loser gets a unique violation.
-- Also serves the availability query (doctor + slot range).
CREATE UNIQUE INDEX uq_bookings_active_slot ON bookings (doctor_id, slot_start)
    WHERE status IN ('HELD', 'CONFIRMED');

-- Supports the housekeeping sweep that expires overdue holds.
CREATE INDEX idx_bookings_held_expiry ON bookings (hold_expires_at) WHERE status = 'HELD';

INSERT INTO doctors (name, specialty) VALUES
    ('Dr. Asha Menon', 'General Medicine'),
    ('Dr. Rahul Nair', 'Pediatrics'),
    ('Dr. Priya Iyer', 'Dermatology');
