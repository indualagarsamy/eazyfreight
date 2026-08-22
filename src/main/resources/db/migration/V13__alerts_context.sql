-- Alerts context.
--
-- The Alerts track owns no business data. Every row here is a statement about
-- something happening in one of the other five contexts, which is why alerts holds
-- a booking_id and nothing else from the operational side — no cargo, no vessel, no
-- amounts. What it does own is the record of who was told, when, and what they did
-- about it, and that record is the whole point: in the monolith it does not exist,
-- so nobody can say afterwards whether a missed sailing was a surprise or a warning
-- everyone ignored.
--
-- alert_history is append-only and ordered by an explicit sequence number rather
-- than a timestamp. Creation and the first notification land in the same
-- transaction on the same clock tick, and sorting those by time returns them in
-- whatever order the planner feels like.

CREATE TABLE alerts (
    id                   UUID          PRIMARY KEY,
    booking_id           UUID          NOT NULL REFERENCES bookings (id),
    booking_reference    VARCHAR(32)   NOT NULL,
    alert_type           VARCHAR(64)   NOT NULL,
    track                VARCHAR(16)   NOT NULL,
    category             VARCHAR(16)   NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    title                VARCHAR(128)  NOT NULL,
    message              VARCHAR(1024) NOT NULL,
    recommended_action   VARCHAR(512)  NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    deadline_at          TIMESTAMP,
    last_evaluated_at    TIMESTAMP     NOT NULL,
    acknowledged_at      TIMESTAMP,
    acknowledged_by      VARCHAR(64),
    snoozed_until        TIMESTAMP,
    snoozed_by           VARCHAR(64),
    escalated_at         TIMESTAMP,
    escalated_to         VARCHAR(32),
    resolved_at          TIMESTAMP,
    resolved_by          VARCHAR(64),
    resolution_reason    VARCHAR(128),

    -- Closing an alert without saying why is how a problem becomes a mystery.
    CONSTRAINT ck_alerts_resolution_has_reason
        CHECK (status <> 'RESOLVED' OR resolution_reason IS NOT NULL),

    -- A snoozed alert must have something to wake up from.
    CONSTRAINT ck_alerts_snoozed_has_expiry
        CHECK (status <> 'SNOOZED' OR snoozed_until IS NOT NULL)
);

CREATE INDEX ix_alerts_booking ON alerts (booking_id);
CREATE INDEX ix_alerts_status ON alerts (status);
CREATE INDEX ix_alerts_deadline ON alerts (deadline_at);

CREATE TABLE alert_history (
    id              UUID         PRIMARY KEY,
    alert_id        UUID         NOT NULL REFERENCES alerts (id),
    sequence_number INTEGER      NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    actor           VARCHAR(64)  NOT NULL,
    notes           VARCHAR(512),

    CONSTRAINT ux_alert_history_sequence UNIQUE (alert_id, sequence_number)
);

CREATE INDEX ix_alert_history_alert ON alert_history (alert_id);

CREATE TABLE alert_notifications (
    id              UUID        PRIMARY KEY,
    alert_id        UUID        NOT NULL REFERENCES alerts (id),
    channel         VARCHAR(16) NOT NULL,
    recipient_role  VARCHAR(32) NOT NULL,
    sent_at         TIMESTAMP   NOT NULL,
    delivered_at    TIMESTAMP,
    delivery_status VARCHAR(16) NOT NULL,
    failure_reason  VARCHAR(256),

    -- No implementation transmits email or SMS. The column records that fact per
    -- row so a delivery record can never be mistaken for a message someone got.
    simulated       BOOLEAN     NOT NULL,

    CONSTRAINT ck_alert_notifications_failure_reason
        CHECK (delivery_status <> 'FAILED' OR failure_reason IS NOT NULL)
);

CREATE INDEX ix_alert_notifications_alert ON alert_notifications (alert_id);

-- Per-deployment overrides. A missing row means "use the defaults compiled into
-- AlertType", so an untouched installation still gets every alert.
CREATE TABLE alert_configurations (
    id                    UUID          PRIMARY KEY,
    alert_type            VARCHAR(64)   NOT NULL UNIQUE,
    enabled               BOOLEAN       NOT NULL,
    threshold_days        INTEGER,
    escalation_hours      INTEGER       NOT NULL,
    notification_channels VARCHAR(64)   NOT NULL,
    snooze_max_hours      INTEGER       NOT NULL,
    custom_message        VARCHAR(1024),

    CONSTRAINT ck_alert_config_threshold_positive
        CHECK (threshold_days IS NULL OR threshold_days > 0),
    CONSTRAINT ck_alert_config_escalation_positive
        CHECK (escalation_hours > 0),
    CONSTRAINT ck_alert_config_snooze_positive
        CHECK (snooze_max_hours > 0)
);
