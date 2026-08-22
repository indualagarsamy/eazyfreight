package com.eazyfreight.booking;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Instruction to collect cargo and move it to the port.
 *
 * <p>A real record with its own reference and lifecycle, not the {@code TDOGenerated}
 * bit flag the monolith used — so it is answerable who was dispatched and when.
 */
@Entity
@Table(name = "truck_delivery_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TruckDeliveryOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tdo_reference", nullable = false, unique = true, length = 32)
    private String tdoReference;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "pickup_date_time")
    private Instant pickupDateTime;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "trucking_vendor_id")
    private UUID truckingVendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TruckDeliveryOrderStatus status;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    static TruckDeliveryOrder generate(
            String tdoReference,
            String pickupAddress,
            String deliveryAddress,
            Instant pickupDateTime,
            Instant generatedAt
    ) {
        TruckDeliveryOrder order = new TruckDeliveryOrder();
        order.tdoReference = tdoReference;
        order.pickupAddress = pickupAddress;
        order.deliveryAddress = deliveryAddress;
        order.pickupDateTime = pickupDateTime;
        order.status = TruckDeliveryOrderStatus.GENERATED;
        order.generatedAt = generatedAt;
        return order;
    }

    void dispatch(UUID driverId, UUID truckingVendorId, Instant dispatchedAt) {
        if (status != TruckDeliveryOrderStatus.GENERATED) {
            throw new DomainRuleViolationException(
                    "Truck delivery order " + tdoReference + " has already been dispatched (status " + status + ")");
        }
        if (driverId == null && truckingVendorId == null) {
            throw new DomainRuleViolationException(
                    "Dispatch requires either a driver or a trucking vendor");
        }
        this.driverId = driverId;
        this.truckingVendorId = truckingVendorId;
        this.status = TruckDeliveryOrderStatus.DISPATCHED;
        this.dispatchedAt = dispatchedAt;
    }
}
