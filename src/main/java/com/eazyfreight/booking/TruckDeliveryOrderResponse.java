package com.eazyfreight.booking;

import java.time.Instant;
import java.util.UUID;

public record TruckDeliveryOrderResponse(
        UUID id,
        String tdoReference,
        String pickupAddress,
        String deliveryAddress,
        Instant pickupDateTime,
        UUID driverId,
        UUID truckingVendorId,
        TruckDeliveryOrderStatus status,
        Instant generatedAt,
        Instant dispatchedAt
) {
    public static TruckDeliveryOrderResponse fromEntity(TruckDeliveryOrder order) {
        if (order == null) {
            return null;
        }
        return new TruckDeliveryOrderResponse(
                order.getId(),
                order.getTdoReference(),
                order.getPickupAddress(),
                order.getDeliveryAddress(),
                order.getPickupDateTime(),
                order.getDriverId(),
                order.getTruckingVendorId(),
                order.getStatus(),
                order.getGeneratedAt(),
                order.getDispatchedAt()
        );
    }
}
