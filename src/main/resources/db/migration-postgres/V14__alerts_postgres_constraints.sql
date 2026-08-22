-- PostgreSQL-only: H2 has no partial indexes.

-- Business rule 6, duplicate suppression, as a constraint rather than a convention.
-- At most one unresolved alert of a given type per booking. An acknowledged or
-- snoozed alert still counts as open and still blocks a second one: the condition
-- has not gone away just because somebody has seen it. Without this, a half-hourly
-- sweep on a container that has not moved for a week produces three hundred and
-- thirty-six identical rows, and the alert list becomes something people close
-- rather than read.
CREATE UNIQUE INDEX ux_alerts_one_open_per_booking_and_type
    ON alerts (booking_id, alert_type) WHERE status <> 'RESOLVED';
