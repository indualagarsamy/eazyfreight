package com.eazyfreight.booking;

import java.util.UUID;

/** At least one of driverId or truckingVendorId must be supplied. */
public record DispatchTruckDeliveryOrderRequest(
        UUID driverId,
        UUID truckingVendorId
) {
}
