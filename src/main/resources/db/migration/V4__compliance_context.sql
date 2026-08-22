-- Export Compliance context.
--
-- Two rules shape this schema. Filings are immutable once submitted, so a
-- correction or amendment is a NEW row pointing at its parent rather than an
-- UPDATE — there is no path by which what was reported to CBP is overwritten.
-- And itn_records is append-only with a single active row per booking: an
-- amendment supersedes rather than replaces, because "what was reported, and
-- when" must stay answerable for the five-year retention period.

CREATE TABLE export_licenses (
    id                 UUID          PRIMARY KEY,
    license_number     VARCHAR(64)   NOT NULL,
    issuing_authority  VARCHAR(64)   NOT NULL,
    license_type       VARCHAR(64)   NOT NULL,
    commodity_eccn     VARCHAR(32),
    valid_from         DATE          NOT NULL,
    valid_until        DATE          NOT NULL,
    value_authorized   NUMERIC(14,2),
    CONSTRAINT ck_export_licenses_validity CHECK (valid_until >= valid_from)
);

CREATE TABLE eei_filings (
    id                            UUID          PRIMARY KEY,
    booking_id                    UUID          NOT NULL REFERENCES bookings (id),
    filing_reference              VARCHAR(32)   NOT NULL UNIQUE,
    filing_type                   VARCHAR(16)   NOT NULL,
    parent_filing_id              UUID          REFERENCES eei_filings (id),
    status                        VARCHAR(16)   NOT NULL,

    shipper_name                  VARCHAR(128),
    shipper_ein                   VARCHAR(32),
    shipper_address               VARCHAR(255),
    consignee_name                VARCHAR(128),
    consignee_address             VARCHAR(255),
    consignee_country             VARCHAR(2),

    schedule_b_number             VARCHAR(16),
    schedule_b_translated         BOOLEAN       NOT NULL,
    commodity_description         VARCHAR(150),
    quantity_value                NUMERIC(14,3),
    quantity_unit                 VARCHAR(16),
    value_usd                     NUMERIC(14,2),

    carrier_scac                  VARCHAR(8),
    vessel_name                   VARCHAR(128),
    voyage_number                 VARCHAR(64),
    port_of_export_code           VARCHAR(8),
    country_of_destination        VARCHAR(2),
    estimated_etd                 DATE,

    submitted_at                  TIMESTAMP(6) WITH TIME ZONE,
    submitted_by                  VARCHAR(64),
    accepted_at                   TIMESTAMP(6) WITH TIME ZONE,
    rejected_at                   TIMESTAMP(6) WITH TIME ZONE,
    rejection_reason_code         VARCHAR(16),
    rejection_reason_description  VARCHAR(255),
    cancelled_at                  TIMESTAMP(6) WITH TIME ZONE,
    cancellation_reason           VARCHAR(255),
    aes_submission_reference      VARCHAR(64),
    simulated                     BOOLEAN       NOT NULL,
    amendment_reason              VARCHAR(255),
    license_required              BOOLEAN       NOT NULL,
    created_at                    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    export_license_id             UUID          REFERENCES export_licenses (id),

    -- An amendment or correction must say what it supersedes.
    CONSTRAINT ck_eei_filings_amendment_has_parent
        CHECK (filing_type <> 'AMENDMENT' OR parent_filing_id IS NOT NULL),
    -- A rejection must carry CBP's reason, for audit.
    CONSTRAINT ck_eei_filings_rejection_recorded
        CHECK (status <> 'REJECTED'
               OR (rejection_reason_code IS NOT NULL AND rejected_at IS NOT NULL)),
    CONSTRAINT ck_eei_filings_cancellation_recorded
        CHECK (status <> 'CANCELLED'
               OR (cancellation_reason IS NOT NULL AND cancelled_at IS NOT NULL))
);

CREATE INDEX ix_eei_filings_booking ON eei_filings (booking_id);
CREATE INDEX ix_eei_filings_status ON eei_filings (status);
CREATE INDEX ix_eei_filings_parent ON eei_filings (parent_filing_id);

-- Append-only. An amendment supersedes a row here; nothing deletes one.
CREATE TABLE itn_records (
    id                     UUID         PRIMARY KEY,
    eei_filing_id          UUID         NOT NULL REFERENCES eei_filings (id),
    booking_id             UUID         NOT NULL REFERENCES bookings (id),
    itn_number             VARCHAR(16)  NOT NULL,
    issued_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    is_active              BOOLEAN      NOT NULL,
    superseded_by_itn_id   UUID         REFERENCES itn_records (id),
    recorded_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_by            VARCHAR(64)  NOT NULL,
    -- CBP's format: the letter X followed by 14 digits. Expressed portably; the
    -- exact pattern is enforced by the ItnNumber value object and, on PostgreSQL,
    -- by a regex constraint in db/migration-postgres.
    CONSTRAINT ck_itn_records_format
        CHECK (itn_number LIKE 'X%' AND LENGTH(itn_number) = 15),
    -- A superseded record cannot still be the active one.
    CONSTRAINT ck_itn_records_superseded_inactive
        CHECK (superseded_by_itn_id IS NULL OR is_active = FALSE)
);

CREATE INDEX ix_itn_records_filing ON itn_records (eei_filing_id);

-- "At most one active ITN per booking" needs a partial unique index, which is
-- PostgreSQL-only; it lives in db/migration-postgres. The EEIFiling aggregate
-- enforces the same invariant in code, so H2-backed tests still cover it.
CREATE INDEX ix_itn_records_active ON itn_records (booking_id, is_active);

-- Append-only audit log, retained five years per CBP regulation.
CREATE TABLE eei_filing_history (
    id               UUID         PRIMARY KEY,
    eei_filing_id    UUID         NOT NULL REFERENCES eei_filings (id),
    sequence_number  INTEGER      NOT NULL,
    from_status      VARCHAR(16),
    to_status        VARCHAR(16)  NOT NULL,
    occurred_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    actor            VARCHAR(64)  NOT NULL,
    detail           VARCHAR(255)
);

CREATE UNIQUE INDEX ux_eei_filing_history_sequence
    ON eei_filing_history (eei_filing_id, sequence_number);
