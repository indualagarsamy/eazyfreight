-- PostgreSQL-only. H2 has no partial indexes, so this cannot live in db/migration.

-- At most one seal may be active on a container at any time. The aggregate enforces
-- the same rule; this makes it true of the data regardless of how it got there.
CREATE UNIQUE INDEX ux_seal_records_one_active_per_booking
    ON seal_records (booking_id) WHERE is_active;

-- At most one live dispatch per movement type per booking. A FAILED dispatch is
-- excluded so a rejected delivery can be re-attempted.
CREATE UNIQUE INDEX ux_truck_dispatches_one_live_per_movement
    ON truck_dispatches (booking_id, movement_type) WHERE status <> 'FAILED';
