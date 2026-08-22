-- Documentation context — the convergence point.
--
-- Everything here is a snapshot. A Bill of Lading is a document of title, so it
-- must record the facts as they stood when it was issued: party names and
-- addresses are copied in, not referenced, because a customer changing address
-- next year must not alter a BOL already in a consignee's hands.
--
-- house_bols is the table the monolith most conspicuously lacks. There, an
-- amendment saves over the same Word file and the revision someone is holding
-- stops existing. Here each revision is a row, the BOL number is stable across
-- them, and exactly one is active.

CREATE TABLE master_bol_instructions (
    id                          UUID          PRIMARY KEY,
    booking_id                  UUID          NOT NULL REFERENCES bookings (id),
    instructions_reference      VARCHAR(32)   NOT NULL UNIQUE,
    status                      VARCHAR(16)   NOT NULL,
    supersedes_instructions_id  UUID          REFERENCES master_bol_instructions (id),
    carrier_id                  UUID,
    carrier_booking_ref         VARCHAR(64),

    -- The three late-arriving facts, proving what was sent to the carrier.
    container_number            VARCHAR(24)   NOT NULL,
    seal_number                 VARCHAR(64)   NOT NULL,
    itn_number                  VARCHAR(16)   NOT NULL,

    shipper_name                VARCHAR(128)  NOT NULL,
    shipper_address             VARCHAR(255),
    consignee_name              VARCHAR(128)  NOT NULL,
    consignee_address           VARCHAR(255),
    notify_party_name           VARCHAR(128),
    notify_party_address        VARCHAR(255),

    port_of_loading_code        VARCHAR(8)    NOT NULL,
    port_of_discharge_code      VARCHAR(8)    NOT NULL,
    vessel_name                 VARCHAR(128),
    voyage_number               VARCHAR(64),
    cargo_description           VARCHAR(255)  NOT NULL,
    hs_code                     VARCHAR(16),
    actual_weight_kg            NUMERIC(12,3),
    actual_pieces               INTEGER,
    actual_cbm                  NUMERIC(12,4),
    marks_and_numbers           VARCHAR(255),
    freight_terms               VARCHAR(16)   NOT NULL,

    drafted_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    drafted_by                  VARCHAR(64)   NOT NULL,
    approved_at                 TIMESTAMP(6) WITH TIME ZONE,
    approved_by                 VARCHAR(64),
    sent_at                     TIMESTAMP(6) WITH TIME ZONE,
    documentation_cut_off_date  DATE,
    sent_after_cut_off          BOOLEAN       NOT NULL,
    carrier_query               VARCHAR(255),
    -- Sent instructions must carry a timestamp; that is the proof of despatch.
    CONSTRAINT ck_instructions_sent_stamped
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);

CREATE INDEX ix_instructions_booking ON master_bol_instructions (booking_id);

CREATE TABLE master_bols (
    id                        UUID         PRIMARY KEY,
    booking_id                UUID         NOT NULL REFERENCES bookings (id),
    instructions_id           UUID         NOT NULL REFERENCES master_bol_instructions (id),
    master_bol_number         VARCHAR(64)  NOT NULL,
    carrier_id                UUID,
    issued_by_carrier_at      TIMESTAMP(6) WITH TIME ZONE,
    received_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    received_by               VARCHAR(64)  NOT NULL,
    verification_status       VARCHAR(24)  NOT NULL,
    verified_at               TIMESTAMP(6) WITH TIME ZONE,
    verified_by               VARCHAR(64),
    discrepancy_raised_at     TIMESTAMP(6) WITH TIME ZONE,
    discrepancy_resolved_at   TIMESTAMP(6) WITH TIME ZONE,
    document_file_reference   VARCHAR(255),
    CONSTRAINT ck_master_bol_verified_stamped
        CHECK (verification_status <> 'VERIFIED' OR verified_at IS NOT NULL)
);

CREATE INDEX ix_master_bols_booking ON master_bols (booking_id);

-- Named fields that differ from the instructions, rather than a free-text note.
CREATE TABLE master_bol_discrepancies (
    master_bol_id  UUID        NOT NULL REFERENCES master_bols (id),
    field_name     VARCHAR(64)
);

CREATE INDEX ix_master_bol_discrepancies ON master_bol_discrepancies (master_bol_id);

CREATE TABLE original_bol_tracking (
    id                     UUID         PRIMARY KEY,
    house_bol_id           UUID         NOT NULL,
    originals_issued       INTEGER      NOT NULL,
    originals_surrendered  INTEGER      NOT NULL,
    released_at            TIMESTAMP(6) WITH TIME ZONE,
    released_to            VARCHAR(128),
    courier_reference      VARCHAR(64),
    surrendered_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT ck_originals_not_over_surrendered
        CHECK (originals_surrendered BETWEEN 0 AND originals_issued)
);

CREATE TABLE house_bols (
    id                             UUID          PRIMARY KEY,
    booking_id                     UUID          NOT NULL REFERENCES bookings (id),
    master_bol_id                  UUID          NOT NULL REFERENCES master_bols (id),
    -- Stable across every revision.
    house_bol_number               VARCHAR(32)   NOT NULL,
    revision_number                INTEGER       NOT NULL,
    is_active                      BOOLEAN       NOT NULL,
    status                         VARCHAR(16)   NOT NULL,
    release_type                   VARCHAR(24)   NOT NULL,
    release_type_confirmed_at      TIMESTAMP(6) WITH TIME ZONE,
    release_type_confirmed_by      VARCHAR(64),

    -- Party snapshots. Deliberately not foreign keys.
    shipper_id                     UUID,
    shipper_name_snapshot          VARCHAR(128)  NOT NULL,
    shipper_address_snapshot       VARCHAR(255),
    consignee_id                   UUID,
    consignee_name_snapshot        VARCHAR(128)  NOT NULL,
    consignee_address_snapshot     VARCHAR(255),
    notify_party_name_snapshot     VARCHAR(128),
    notify_party_address_snapshot  VARCHAR(255),

    port_of_loading_code           VARCHAR(8)    NOT NULL,
    port_of_discharge_code         VARCHAR(8)    NOT NULL,
    vessel_name                    VARCHAR(128),
    voyage_number                  VARCHAR(64),
    container_number               VARCHAR(24)   NOT NULL,
    seal_number                    VARCHAR(64)   NOT NULL,
    cargo_description              VARCHAR(255)  NOT NULL,
    hs_code                        VARCHAR(16),
    weight_kg                      NUMERIC(12,3),
    pieces                         INTEGER,
    cbm                            NUMERIC(12,4),
    marks_and_numbers              VARCHAR(255),
    freight_terms                  VARCHAR(16)   NOT NULL,

    issued_at                      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    issued_by                      VARCHAR(64)   NOT NULL,
    voided_at                      TIMESTAMP(6) WITH TIME ZONE,
    superseded_by_house_bol_id     UUID          REFERENCES house_bols (id),
    amendment_reason               VARCHAR(255),
    pdf_reference                  VARCHAR(255),
    original_bol_tracking_id       UUID          REFERENCES original_bol_tracking (id),

    -- The number plus the revision is the real identity.
    CONSTRAINT ux_house_bol_number_revision UNIQUE (house_bol_number, revision_number),
    CONSTRAINT ck_house_bol_voided_inactive
        CHECK (status <> 'VOIDED' OR is_active = FALSE),
    CONSTRAINT ck_house_bol_revision_non_negative CHECK (revision_number >= 0)
);

CREATE INDEX ix_house_bols_booking ON house_bols (booking_id);
CREATE INDEX ix_house_bols_number ON house_bols (house_bol_number);

-- Append-only. Answers who was sent which revision, when, and how.
CREATE TABLE house_bol_distributions (
    id                 UUID         PRIMARY KEY,
    house_bol_id       UUID         NOT NULL REFERENCES house_bols (id),
    recipient          VARCHAR(24)  NOT NULL,
    recipient_name     VARCHAR(128),
    recipient_address  VARCHAR(255),
    channel            VARCHAR(16)  NOT NULL,
    revision_number    INTEGER      NOT NULL,
    sent_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_by            VARCHAR(64)  NOT NULL,
    reference          VARCHAR(64)
);

CREATE INDEX ix_house_bol_distributions ON house_bol_distributions (house_bol_id, sent_at);
