-- Declared cargo value, needed by the Export Compliance track.
--
-- The obligation to file an EEI turns on value exceeding $2,500 per Schedule B
-- number, so the figure has to exist on the booking before a filing can be
-- assessed. Nullable because bookings created before this column have none;
-- new bookings are required to supply it by request validation.

ALTER TABLE booking_cargo_details
    ADD COLUMN value_usd NUMERIC(14,2);

COMMENT ON COLUMN booking_cargo_details.value_usd IS
    'Declared value of the goods in USD, as filed on the EEI.';
