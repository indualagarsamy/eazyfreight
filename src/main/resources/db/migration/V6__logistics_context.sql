-- Container and Equipment context.
--
-- The shapes here are the direct answer to what the monolith lost. Two truck
-- dispatches instead of one PickupDate column. An append-only seal history instead
-- of a SealNo field that a customs inspection silently overwrote. A terminal
-- acceptance that computes storage-fee exposure at the gate rather than discovering
-- it when the invoice arrives.

CREATE TABLE terminal_acceptances (
    id                      UUID          PRIMARY KEY,
    booking_id              UUID          NOT NULL REFERENCES bookings (id),
    container_number        VARCHAR(24)   NOT NULL,
    gate_receipt_number     VARCHAR(64)   NOT NULL,
    terminal_name           VARCHAR(128)  NOT NULL,
    accepted_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    earliest_acceptance_date DATE,
    vessel_cut_off_date     DATE,
    storage_fee_applies     BOOLEAN       NOT NULL,
    storage_fee_daily_rate  NUMERIC(12,2),
    days_early              INTEGER
);

CREATE TABLE actual_cargo_details (
    id                UUID          PRIMARY KEY,
    booking_id        UUID          NOT NULL REFERENCES bookings (id),
    actual_weight_kg  NUMERIC(12,3) NOT NULL,
    actual_pieces     INTEGER       NOT NULL,
    actual_cbm        NUMERIC(12,4),
    booked_weight_kg  NUMERIC(12,3) NOT NULL,
    booked_pieces     INTEGER       NOT NULL,
    booked_cbm        NUMERIC(12,4),
    recorded_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_by       VARCHAR(64)   NOT NULL,
    CONSTRAINT ck_actual_cargo_positive CHECK (actual_weight_kg > 0 AND actual_pieces > 0)
);

CREATE TABLE container_assignments (
    id                        UUID         PRIMARY KEY,
    -- One container journey per booking; FCL multi-container is not modelled.
    booking_id                UUID         NOT NULL UNIQUE REFERENCES bookings (id),
    container_number          VARCHAR(24),
    container_type            VARCHAR(16),
    assigned_at               TIMESTAMP(6) WITH TIME ZONE,
    assigned_by               VARCHAR(64),
    source                    VARCHAR(24),
    stage                     VARCHAR(32)  NOT NULL,
    itn_received              BOOLEAN      NOT NULL,
    itn_number                VARCHAR(16),
    loading_completed_at      TIMESTAMP(6) WITH TIME ZONE,
    loaded_on_vessel_at       TIMESTAMP(6) WITH TIME ZONE,
    vessel_departed_at        TIMESTAMP(6) WITH TIME ZONE,
    created_at                TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    terminal_acceptance_id    UUID         REFERENCES terminal_acceptances (id),
    actual_cargo_details_id   UUID         REFERENCES actual_cargo_details (id),
    -- The container number arrives late, but once known it is stamped with who and when.
    CONSTRAINT ck_container_assignment_number_stamped
        CHECK (container_number IS NULL OR (assigned_at IS NOT NULL AND assigned_by IS NOT NULL))
);

CREATE INDEX ix_container_assignments_stage ON container_assignments (stage);
CREATE INDEX ix_container_assignments_container ON container_assignments (container_number);

-- Append-only. A replacement deactivates its predecessor; nothing is deleted.
CREATE TABLE seal_records (
    id                      UUID         PRIMARY KEY,
    container_assignment_id UUID         NOT NULL REFERENCES container_assignments (id),
    booking_id              UUID         NOT NULL REFERENCES bookings (id),
    seal_number             VARCHAR(64)  NOT NULL,
    seal_source             VARCHAR(24)  NOT NULL,
    is_active               BOOLEAN      NOT NULL,
    issued_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deactivated_at          TIMESTAMP(6) WITH TIME ZONE,
    deactivation_reason     VARCHAR(32),
    replaced_by_seal_id     UUID         REFERENCES seal_records (id),
    recorded_by             VARCHAR(64)  NOT NULL,
    -- A deactivated seal must say when and why.
    CONSTRAINT ck_seal_records_deactivation
        CHECK (is_active = TRUE
               OR (deactivated_at IS NOT NULL AND deactivation_reason IS NOT NULL))
);

CREATE INDEX ix_seal_records_assignment ON seal_records (container_assignment_id);
CREATE INDEX ix_seal_records_active ON seal_records (booking_id, is_active);

CREATE TABLE truck_dispatches (
    id                          UUID         PRIMARY KEY,
    container_assignment_id     UUID         NOT NULL REFERENCES container_assignments (id),
    booking_id                  UUID         NOT NULL REFERENCES bookings (id),
    movement_type               VARCHAR(16)  NOT NULL,
    tdo_reference               VARCHAR(48)  NOT NULL,
    driver_id                   UUID,
    trucking_vendor_id          UUID,
    vehicle_reference           VARCHAR(64),
    pickup_address              VARCHAR(255) NOT NULL,
    pickup_address_type         VARCHAR(24)  NOT NULL,
    delivery_address            VARCHAR(255) NOT NULL,
    delivery_address_type       VARCHAR(24)  NOT NULL,
    scheduled_pickup_date       TIMESTAMP(6) WITH TIME ZONE,
    actual_pickup_date          TIMESTAMP(6) WITH TIME ZONE,
    scheduled_delivery_date     TIMESTAMP(6) WITH TIME ZONE,
    actual_delivery_date        TIMESTAMP(6) WITH TIME ZONE,
    status                      VARCHAR(16)  NOT NULL,
    dispatched_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    dispatched_by               VARCHAR(64)  NOT NULL,
    delivery_receipt_reference  VARCHAR(64),
    notes                       VARCHAR(255),
    -- Either our own driver or a vendor, and a vendor must carry a vehicle reference.
    CONSTRAINT ck_truck_dispatch_has_carrier
        CHECK (driver_id IS NOT NULL OR trucking_vendor_id IS NOT NULL),
    CONSTRAINT ck_truck_dispatch_vendor_vehicle
        CHECK (trucking_vendor_id IS NULL OR vehicle_reference IS NOT NULL)
);

CREATE INDEX ix_truck_dispatches_assignment ON truck_dispatches (container_assignment_id);

CREATE TABLE cbp_examinations (
    id                        UUID         PRIMARY KEY,
    container_assignment_id   UUID         NOT NULL REFERENCES container_assignments (id),
    booking_id                UUID         NOT NULL REFERENCES bookings (id),
    container_number          VARCHAR(24),
    hold_placed_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    examination_started_at    TIMESTAMP(6) WITH TIME ZONE,
    examination_completed_at  TIMESTAMP(6) WITH TIME ZONE,
    result                    VARCHAR(24),
    original_seal_id          UUID         REFERENCES seal_records (id),
    replacement_seal_id       UUID         REFERENCES seal_records (id),
    cbp_officer_id            VARCHAR(64),
    notes                     VARCHAR(255),
    -- A completed examination must record its outcome.
    CONSTRAINT ck_cbp_examination_completed
        CHECK (examination_completed_at IS NULL OR result IS NOT NULL)
);

CREATE INDEX ix_cbp_examinations_assignment ON cbp_examinations (container_assignment_id);
