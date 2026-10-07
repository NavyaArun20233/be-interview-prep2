CREATE TABLE books (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    author         VARCHAR(255) NOT NULL,
    isbn           VARCHAR(13)  NOT NULL,
    published_year INTEGER,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_books_isbn UNIQUE (isbn)
);

-- Default list order (title, id).
CREATE INDEX idx_books_title ON books (title, id);

-- Loan history is part of the book: deleting a book (allowed only when it is not on loan) removes its past loans.
CREATE TABLE loans (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id     BIGINT       NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    member_name VARCHAR(100) NOT NULL,
    borrowed_at TIMESTAMPTZ  NOT NULL,
    returned_at TIMESTAMPTZ,
    CONSTRAINT chk_loans_returned_after_borrowed CHECK (returned_at IS NULL OR returned_at >= borrowed_at)
);

CREATE INDEX idx_loans_book_id ON loans (book_id);

-- At most one open loan per book: the database rejects a second concurrent borrow even if the service check races.
CREATE UNIQUE INDEX uq_loans_active_book ON loans (book_id) WHERE returned_at IS NULL;
