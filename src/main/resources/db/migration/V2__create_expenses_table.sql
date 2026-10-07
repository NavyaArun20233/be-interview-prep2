CREATE TABLE expenses (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amount       NUMERIC(12, 2) NOT NULL,
    category     VARCHAR(20)    NOT NULL,
    expense_date DATE           NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_expenses_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_expenses_category CHECK (category IN ('FOOD', 'TRAVEL', 'BILLS', 'OTHER'))
);

-- Date-range listing and the monthly summary filter on expense_date; the category filter combines both.
CREATE INDEX idx_expenses_expense_date ON expenses (expense_date);
CREATE INDEX idx_expenses_category_expense_date ON expenses (category, expense_date);
