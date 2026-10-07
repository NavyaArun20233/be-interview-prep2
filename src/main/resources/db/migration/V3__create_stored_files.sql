-- Metadata of uploaded files. The bytes live on disk under app.storage.location, named by stored_name
-- (a server-generated UUID), so no user input ever becomes part of a filesystem path.
CREATE TABLE stored_files (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(64)  NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_stored_files_stored_name UNIQUE (stored_name),
    CONSTRAINT ck_stored_files_size_bytes_positive CHECK (size_bytes > 0)
);

-- The list endpoint pages newest first.
CREATE INDEX idx_stored_files_uploaded_at ON stored_files (uploaded_at DESC, id DESC);
