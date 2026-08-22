-- Retire the Booking context's TruckDeliveryOrder.
--
-- Two contexts modelled the same thing. The Ocean Booking specification describes
-- a single truck delivery order; the Container and Equipment specification
-- describes two distinct movements — empty container out to the customer, loaded
-- container in to the port — each with its own driver, timeline and receipt, and
-- an ITN gate on the second. Both were implemented faithfully, which left a
-- booking able to report "no truck order raised" for a container that had already
-- been delivered.
--
-- The Logistics model is strictly more capable, so the Booking one goes. Nothing
-- is lost: truck_delivery_orders was never populated once truck_dispatches
-- existed. transport_required and pickup_address stay on the booking, because
-- whether we arrange trucking and where from are booking facts the Logistics
-- context reads.

ALTER TABLE bookings DROP COLUMN truck_delivery_order_id;

DROP TABLE truck_delivery_orders;
