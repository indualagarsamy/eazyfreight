-- Finance context.
--
-- carrier_payables is the table that encodes the most important financial rule in
-- the business. Eazy Freight buys vessel space on the customer's behalf and pays
-- the carrier out of the customer's money, not its own — so a payable cannot exist
-- without the customer payment that funds it. customer_payment_id is NOT NULL and
-- UNIQUE, which makes that causal chain a property of the schema rather than a
-- convention. The monolith has two independent status flags and a T+2 rule that
-- lives in the accountant's memory.
--
-- invoice_lines carry buy and sell side by side, so profit-per-file is a query
-- rather than a month-end reconciliation across QuickBooks and a spreadsheet.

CREATE TABLE invoices (
    id                             UUID          PRIMARY KEY,
    invoice_number                 VARCHAR(32)   NOT NULL UNIQUE,
    booking_id                     UUID          NOT NULL REFERENCES bookings (id),
    house_bol_id                   UUID,
    customer_id                    UUID          NOT NULL,
    status                         VARCHAR(16)   NOT NULL,
    invoice_type                   VARCHAR(16)   NOT NULL,
    payment_terms_type             VARCHAR(32)   NOT NULL,
    invoice_date                   DATE,
    payment_due_date               DATE,
    confirmed_eta                  DATE,
    total_amount                   NUMERIC(14,2) NOT NULL,
    total_buy_amount               NUMERIC(14,2) NOT NULL,
    currency                       VARCHAR(3)    NOT NULL,
    paid_amount                    NUMERIC(14,2) NOT NULL,
    actuals_confirmed              BOOLEAN       NOT NULL,
    issued_at                      TIMESTAMP(6) WITH TIME ZONE,
    issued_by                      VARCHAR(64),
    pdf_sent_at                    TIMESTAMP(6) WITH TIME ZONE,
    voided_at                      TIMESTAMP(6) WITH TIME ZONE,
    voided_by                      VARCHAR(64),
    void_reason                    VARCHAR(255),
    credit_note_against_invoice_id UUID          REFERENCES invoices (id),
    notes                          VARCHAR(255),
    created_at                     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- An issued invoice must be dated and have a due date.
    CONSTRAINT ck_invoices_issued_dated
        CHECK (status NOT IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
               OR (invoice_date IS NOT NULL AND payment_due_date IS NOT NULL)),
    CONSTRAINT ck_invoices_voided_reason
        CHECK (status <> 'VOIDED' OR (void_reason IS NOT NULL AND voided_at IS NOT NULL)),
    CONSTRAINT ck_invoices_paid_not_negative CHECK (paid_amount >= 0)
);

CREATE INDEX ix_invoices_booking ON invoices (booking_id);
CREATE INDEX ix_invoices_customer ON invoices (customer_id);
CREATE INDEX ix_invoices_due ON invoices (status, payment_due_date);

CREATE TABLE invoice_lines (
    id           UUID          PRIMARY KEY,
    invoice_id   UUID          NOT NULL REFERENCES invoices (id),
    line_number  INTEGER       NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    buy_amount   NUMERIC(14,2) NOT NULL,
    sell_amount  NUMERIC(14,2) NOT NULL,
    quantity     NUMERIC(12,4) NOT NULL,
    unit         VARCHAR(16),
    CONSTRAINT ux_invoice_lines_number UNIQUE (invoice_id, line_number)
);

CREATE TABLE customer_payments (
    id              UUID          PRIMARY KEY,
    invoice_id      UUID          NOT NULL REFERENCES invoices (id),
    amount          NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    payment_date    DATE          NOT NULL,
    payment_method  VARCHAR(16)   NOT NULL,
    reference       VARCHAR(64),
    recorded_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_by     VARCHAR(64)   NOT NULL,
    CONSTRAINT ck_customer_payments_positive CHECK (amount > 0)
);

CREATE INDEX ix_customer_payments_invoice ON customer_payments (invoice_id);

-- The causal chain, in the schema.
CREATE TABLE carrier_payables (
    id                          UUID          PRIMARY KEY,
    booking_id                  UUID          NOT NULL REFERENCES bookings (id),
    -- NOT NULL and UNIQUE: exactly one payable per customer payment, and none
    -- without one.
    customer_payment_id         UUID          NOT NULL UNIQUE REFERENCES customer_payments (id),
    invoice_id                  UUID          NOT NULL REFERENCES invoices (id),
    carrier_id                  UUID,
    amount                      NUMERIC(14,2) NOT NULL,
    currency                    VARCHAR(3)    NOT NULL,
    customer_payment_date       DATE          NOT NULL,
    -- Customer payment date plus two business days. Never recomputed.
    due_date                    DATE          NOT NULL,
    status                      VARCHAR(32)   NOT NULL,
    carrier_invoice_reference   VARCHAR(64),
    carrier_invoice_amount      NUMERIC(14,2),
    carrier_invoice_received_at TIMESTAMP(6) WITH TIME ZONE,
    approved_at                 TIMESTAMP(6) WITH TIME ZONE,
    approved_by                 VARCHAR(64),
    paid_on                     DATE,
    paid_at                     TIMESTAMP(6) WITH TIME ZONE,
    paid_by                     VARCHAR(64),
    payment_reference           VARCHAR(64),
    created_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- The carrier is never due before the customer has paid.
    CONSTRAINT ck_payables_due_after_payment CHECK (due_date >= customer_payment_date),
    -- Matching precedes approval, approval precedes payment.
    CONSTRAINT ck_payables_matched_before_approval
        CHECK (status NOT IN ('APPROVED', 'PAID') OR carrier_invoice_reference IS NOT NULL),
    CONSTRAINT ck_payables_paid_recorded
        CHECK (status <> 'PAID' OR (paid_on IS NOT NULL AND approved_at IS NOT NULL))
);

CREATE INDEX ix_carrier_payables_booking ON carrier_payables (booking_id);
CREATE INDEX ix_carrier_payables_due ON carrier_payables (status, due_date);

CREATE TABLE storage_fees (
    id              UUID          PRIMARY KEY,
    booking_id      UUID          NOT NULL UNIQUE REFERENCES bookings (id),
    cause           VARCHAR(32)   NOT NULL,
    responsibility  VARCHAR(24)   NOT NULL,
    daily_rate      NUMERIC(12,2) NOT NULL,
    days            INTEGER       NOT NULL,
    amount          NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    period_from     DATE,
    period_to       DATE,
    invoice_id      UUID          REFERENCES invoices (id),
    notes           VARCHAR(255),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_storage_fees_days CHECK (days >= 1),
    -- Only a customer-caused fee is passed through.
    CONSTRAINT ck_storage_fees_invoiced_only_if_customer
        CHECK (invoice_id IS NULL OR responsibility = 'CUSTOMER')
);

CREATE TABLE credit_holds (
    id                    UUID         PRIMARY KEY,
    customer_id           UUID         NOT NULL,
    triggering_booking_id UUID         REFERENCES bookings (id),
    reason                VARCHAR(255) NOT NULL,
    active                BOOLEAN      NOT NULL,
    placed_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    placed_by             VARCHAR(64)  NOT NULL,
    lifted_at             TIMESTAMP(6) WITH TIME ZONE,
    lifted_by             VARCHAR(64),
    CONSTRAINT ck_credit_holds_lifted CHECK (active = TRUE OR lifted_at IS NOT NULL)
);

CREATE INDEX ix_credit_holds_customer ON credit_holds (customer_id, active);
