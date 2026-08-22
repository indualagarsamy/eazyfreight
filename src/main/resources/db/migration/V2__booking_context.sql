-- Booking context.
--
-- booking_status_history and booking_reinstatements are the two tables the monolith
-- has no equivalent of. Between them they answer "what changed, when, who did it,
-- and what was it before" — the questions that could not be answered when a
-- reinstatement overwrote ETD in place.

CREATE TABLE carrier_bookings (
    id                     UUID        PRIMARY KEY,
    booking_source_type    VARCHAR(16) NOT NULL,
    carrier_id             UUID        NOT NULL,
    carrier_booking_ref    VARCHAR(64),
    co_loader_booking_ref  VARCHAR(64),
    vessel_name            VARCHAR(128),
    voyage_number          VARCHAR(64),
    confirmed_etd          DATE,
    confirmed_eta          DATE,
    container_type         VARCHAR(16),
    number_of_containers   INTEGER,
    submitted_at           TIMESTAMP(6) WITH TIME ZONE,
    confirmed_at           TIMESTAMP(6) WITH TIME ZONE,
    confirmed_by           VARCHAR(64),
    -- A co-loader booking must carry its own reference alongside the master one.
    CONSTRAINT ck_carrier_bookings_coloader_ref
        CHECK (booking_source_type <> 'CO_LOADER'
               OR carrier_booking_ref IS NULL
               OR co_loader_booking_ref IS NOT NULL)
);

CREATE TABLE truck_delivery_orders (
    id                   UUID         PRIMARY KEY,
    tdo_reference        VARCHAR(32)  NOT NULL UNIQUE,
    pickup_address       VARCHAR(255) NOT NULL,
    delivery_address     VARCHAR(255) NOT NULL,
    pickup_date_time     TIMESTAMP(6) WITH TIME ZONE,
    driver_id            UUID,
    trucking_vendor_id   UUID,
    status               VARCHAR(16)  NOT NULL,
    generated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    dispatched_at        TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE bookings (
    id                            UUID         PRIMARY KEY,
    booking_reference             VARCHAR(32)  NOT NULL UNIQUE,
    quote_id                      UUID,
    customer_id                   UUID         NOT NULL,
    shipper_id                    UUID         NOT NULL,
    consignee_id                  UUID         NOT NULL,
    notify_party_id               UUID,
    also_notify_id                UUID,
    status                        VARCHAR(32)  NOT NULL,
    shipping_mode                 VARCHAR(16)  NOT NULL,
    origin_port_code              VARCHAR(8)   NOT NULL,
    destination_port_code         VARCHAR(8)   NOT NULL,
    incoterms                     VARCHAR(8)   NOT NULL,
    requested_etd                 DATE         NOT NULL,
    requested_eta                 DATE,
    transport_required            BOOLEAN      NOT NULL,
    pickup_address                VARCHAR(255),
    pickup_date_time              TIMESTAMP(6) WITH TIME ZONE,
    special_instructions          VARCHAR(255),
    marks_and_numbers             VARCHAR(255),
    cancellation_reason           VARCHAR(255),
    cancellation_initiated_by     VARCHAR(32),
    cancelled_at                  TIMESTAMP(6) WITH TIME ZONE,
    etd_variance_acknowledged_at  TIMESTAMP(6) WITH TIME ZONE,
    confirmation_sent_at          TIMESTAMP(6) WITH TIME ZONE,
    itn_filed                     BOOLEAN      NOT NULL,
    created_at                    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by                    VARCHAR(64)  NOT NULL,
    last_modified_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_modified_by              VARCHAR(64)  NOT NULL,
    carrier_booking_id            UUID         REFERENCES carrier_bookings (id),
    truck_delivery_order_id       UUID         REFERENCES truck_delivery_orders (id),
    CONSTRAINT ck_bookings_distinct_ports
        CHECK (origin_port_code <> destination_port_code),
    CONSTRAINT ck_bookings_pickup_address_when_transport
        CHECK (transport_required = FALSE OR pickup_address IS NOT NULL),
    CONSTRAINT ck_bookings_cancellation_recorded
        CHECK (status <> 'CANCELLED'
               OR (cancellation_reason IS NOT NULL
                   AND cancellation_initiated_by IS NOT NULL
                   AND cancelled_at IS NOT NULL))
);

CREATE INDEX ix_bookings_customer ON bookings (customer_id);
CREATE INDEX ix_bookings_status ON bookings (status);
CREATE INDEX ix_bookings_quote ON bookings (quote_id);
CREATE INDEX ix_bookings_carrier_booking ON bookings (carrier_booking_id);

CREATE TABLE booking_cargo_details (
    id                        UUID          PRIMARY KEY,
    booking_id                UUID          NOT NULL REFERENCES bookings (id),
    description               VARCHAR(255)  NOT NULL,
    hs_code                   VARCHAR(16)   NOT NULL,
    pieces                    INTEGER       NOT NULL,
    weight_kg                 NUMERIC(12,3) NOT NULL,
    length_cm                 NUMERIC(12,2) NOT NULL,
    width_cm                  NUMERIC(12,2) NOT NULL,
    height_cm                 NUMERIC(12,2) NOT NULL,
    cbm                       NUMERIC(12,4),
    is_hazmat                 BOOLEAN       NOT NULL,
    is_temperature_controlled BOOLEAN       NOT NULL,
    is_oversized              BOOLEAN       NOT NULL,
    marks_and_numbers         VARCHAR(255),
    CONSTRAINT ck_booking_cargo_pieces CHECK (pieces > 0),
    CONSTRAINT ck_booking_cargo_weight CHECK (weight_kg > 0)
);

CREATE INDEX ix_booking_cargo_details_booking ON booking_cargo_details (booking_id);

-- Append-only. Nothing in the application updates or deletes from this table.
CREATE TABLE booking_status_history (
    id               UUID         PRIMARY KEY,
    booking_id       UUID         NOT NULL REFERENCES bookings (id),
    sequence_number  INTEGER      NOT NULL,
    from_status  VARCHAR(32),
    to_status    VARCHAR(32)  NOT NULL,
    changed_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    changed_by   VARCHAR(64)  NOT NULL,
    reason       VARCHAR(255),
    source       VARCHAR(16)  NOT NULL
);

-- Ordering is by sequence_number, not changed_at: several transitions can share a
-- timestamp, and the order they happened in is not recoverable from the clock.
CREATE UNIQUE INDEX ux_booking_status_history_sequence
    ON booking_status_history (booking_id, sequence_number);

-- Append-only. One row per roll onto a later sailing, holding the sailing left behind.
CREATE TABLE booking_reinstatements (
    id                 UUID         PRIMARY KEY,
    booking_id         UUID         NOT NULL REFERENCES bookings (id),
    previous_vessel    VARCHAR(128),
    previous_voyage    VARCHAR(64),
    previous_etd       DATE,
    previous_eta       DATE,
    new_vessel_name    VARCHAR(128) NOT NULL,
    new_voyage_number  VARCHAR(64)  NOT NULL,
    new_etd            DATE         NOT NULL,
    new_eta            DATE         NOT NULL,
    reinstated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    reinstated_by      VARCHAR(64)  NOT NULL,
    reason             VARCHAR(255) NOT NULL
);

CREATE INDEX ix_booking_reinstatements_booking
    ON booking_reinstatements (booking_id, reinstated_at);
