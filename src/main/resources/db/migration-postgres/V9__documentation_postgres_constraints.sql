-- PostgreSQL-only: H2 has no partial indexes.

-- Exactly one live revision per booking. The aggregate enforces it too, but a
-- document of title deserves the constraint in the data as well.
CREATE UNIQUE INDEX ux_house_bols_one_active_per_booking
    ON house_bols (booking_id) WHERE is_active;
