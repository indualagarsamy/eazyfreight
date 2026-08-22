-- PostgreSQL-only: H2 has no partial indexes.

-- A customer can be on at most one active credit hold.
CREATE UNIQUE INDEX ux_credit_holds_one_active_per_customer
    ON credit_holds (customer_id) WHERE active;

-- One freight invoice per booking. Storage-fee invoices and credit notes are
-- separate documents and deliberately excluded.
CREATE UNIQUE INDEX ux_invoices_one_freight_per_booking
    ON invoices (booking_id) WHERE invoice_type = 'FREIGHT';
