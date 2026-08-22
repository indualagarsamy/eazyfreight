-- PostgreSQL-only constraints, kept out of db/migration because H2 cannot parse
-- them and the schema test runs on H2 in PostgreSQL compatibility mode.
--
-- Both restate invariants the domain already enforces. They are here because an
-- EEI filing is a record of what was reported to the US government, and a defect
-- that let two ITNs be active at once would be discovered at an audit rather than
-- in a test.

-- At most one ITN may be active for a booking at any time.
CREATE UNIQUE INDEX ux_itn_records_one_active_per_booking
    ON itn_records (booking_id) WHERE is_active;

-- CBP's exact ITN format: the letter X followed by 14 digits.
ALTER TABLE itn_records
    ADD CONSTRAINT ck_itn_records_format_exact
    CHECK (itn_number ~ '^X[0-9]{14}$');
