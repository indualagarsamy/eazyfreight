-- Quote context, plus the shared business-reference counter.
--
-- Note the shape of `quotes` against the monolith's Shipments table: buy and sell
-- are separate on every line, validity is a column the domain actually reads, and
-- screening status is part of the record rather than something done on a
-- government website and not written down.

CREATE TABLE reference_sequences (
    prefix      VARCHAR(8)  NOT NULL,
    sequence_year INTEGER   NOT NULL,
    last_value  BIGINT      NOT NULL,
    PRIMARY KEY (prefix, sequence_year)
);

CREATE TABLE lanes (
    id                     UUID        PRIMARY KEY,
    origin_port_code       VARCHAR(8)  NOT NULL,
    destination_port_code  VARCHAR(8)  NOT NULL,
    mode                   VARCHAR(16) NOT NULL,
    CONSTRAINT uq_lanes_origin_destination_mode
        UNIQUE (origin_port_code, destination_port_code, mode),
    CONSTRAINT ck_lanes_distinct_ports
        CHECK (origin_port_code <> destination_port_code)
);

CREATE TABLE rates (
    id            UUID          PRIMARY KEY,
    lane_id       UUID          NOT NULL REFERENCES lanes (id),
    carrier_id    UUID          NOT NULL,
    buy_rate      NUMERIC(12,2) NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    rate_type     VARCHAR(16)   NOT NULL,
    unit          VARCHAR(8)    NOT NULL,
    transit_days  INTEGER,
    valid_from    DATE          NOT NULL,
    valid_until   DATE          NOT NULL,
    CONSTRAINT ck_rates_validity CHECK (valid_until >= valid_from)
);

CREATE INDEX ix_rates_lane ON rates (lane_id);

CREATE TABLE surcharges (
    id              UUID          PRIMARY KEY,
    rate_id         UUID          REFERENCES rates (id),
    lane_id         UUID          NOT NULL REFERENCES lanes (id),
    carrier_id      UUID,
    surcharge_type  VARCHAR(32)   NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    valid_from      DATE          NOT NULL,
    valid_until     DATE          NOT NULL
);

CREATE INDEX ix_surcharges_rate ON surcharges (rate_id);
CREATE INDEX ix_surcharges_lane ON surcharges (lane_id);

CREATE TABLE quotes (
    id                      UUID          PRIMARY KEY,
    quote_reference         VARCHAR(32)   NOT NULL UNIQUE,
    customer_id             UUID          NOT NULL,
    status                  VARCHAR(16)   NOT NULL,
    shipping_mode           VARCHAR(16)   NOT NULL,
    origin_port_code        VARCHAR(8)    NOT NULL,
    destination_port_code   VARCHAR(8)    NOT NULL,
    incoterms               VARCHAR(8)    NOT NULL,
    selected_carrier_id     UUID,
    screening_status        VARCHAR(16)   NOT NULL,
    screening_reference_id  VARCHAR(64),
    requested_etd           DATE,
    valid_from              DATE,
    valid_until             DATE,
    rate_valid_until        DATE,
    created_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_at                 TIMESTAMP(6) WITH TIME ZONE,
    accepted_at             TIMESTAMP(6) WITH TIME ZONE,
    declined_at             TIMESTAMP(6) WITH TIME ZONE,
    expired_at              TIMESTAMP(6) WITH TIME ZONE,
    decline_reason          VARCHAR(255),
    notes                   VARCHAR(255),
    total_buy_rate          NUMERIC(12,2) NOT NULL,
    total_sell_rate         NUMERIC(12,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    is_spot_rate            BOOLEAN       NOT NULL,
    special_handling        VARCHAR(255),
    insurance_required      BOOLEAN       NOT NULL,
    CONSTRAINT ck_quotes_distinct_ports
        CHECK (origin_port_code <> destination_port_code),
    -- A quote can only carry a selected carrier once the customer has accepted it.
    CONSTRAINT ck_quotes_carrier_only_when_accepted
        CHECK (selected_carrier_id IS NULL OR status = 'ACCEPTED')
);

CREATE INDEX ix_quotes_customer ON quotes (customer_id);
CREATE INDEX ix_quotes_status ON quotes (status);
CREATE INDEX ix_quotes_open_lane ON quotes (customer_id, origin_port_code, destination_port_code);
CREATE INDEX ix_quotes_valid_until ON quotes (status, valid_until);

CREATE TABLE quote_lines (
    id           UUID          PRIMARY KEY,
    quote_id     UUID          NOT NULL REFERENCES quotes (id),
    line_type    VARCHAR(32)   NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    buy_rate     NUMERIC(12,2) NOT NULL,
    sell_rate    NUMERIC(12,2) NOT NULL,
    currency     VARCHAR(3)    NOT NULL,
    quantity     NUMERIC(12,4) NOT NULL,
    unit         VARCHAR(8)    NOT NULL
);

CREATE INDEX ix_quote_lines_quote ON quote_lines (quote_id);

CREATE TABLE quote_cargo_details (
    id                        UUID          PRIMARY KEY,
    quote_id                  UUID          NOT NULL REFERENCES quotes (id),
    description               VARCHAR(255)  NOT NULL,
    hs_code                   VARCHAR(255)  NOT NULL,
    pieces                    INTEGER       NOT NULL,
    weight_kg                 NUMERIC(12,3) NOT NULL,
    length_cm                 NUMERIC(12,2) NOT NULL,
    width_cm                  NUMERIC(12,2) NOT NULL,
    height_cm                 NUMERIC(12,2) NOT NULL,
    volumetric_weight_cbm     NUMERIC(12,4),
    volumetric_weight_kg      NUMERIC(12,4),
    chargeable_weight         NUMERIC(12,4),
    chargeable_unit           VARCHAR(8),
    is_hazmat                 BOOLEAN       NOT NULL,
    is_temperature_controlled BOOLEAN       NOT NULL,
    is_oversized              BOOLEAN       NOT NULL,
    CONSTRAINT ck_quote_cargo_pieces CHECK (pieces > 0),
    CONSTRAINT ck_quote_cargo_weight CHECK (weight_kg > 0)
);

CREATE INDEX ix_quote_cargo_details_quote ON quote_cargo_details (quote_id);
