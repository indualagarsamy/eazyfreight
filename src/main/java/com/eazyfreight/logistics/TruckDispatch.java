package com.eazyfreight.logistics;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One truck movement. There are two per shipment when we arrange transport, and
 * they are deliberately separate records — different drivers, different days,
 * different receipts.
 */
@Entity
@Table(name = "truck_dispatches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TruckDispatch {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "container_assignment_id", nullable = false)
    private ContainerAssignment assignment;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 16)
    private MovementType movementType;

    @Column(name = "tdo_reference", nullable = false, length = 48)
    private String tdoReference;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "trucking_vendor_id")
    private UUID truckingVendorId;

    /** Truck plate or vendor reference; required for audit when a vendor is used. */
    @Column(name = "vehicle_reference", length = 64)
    private String vehicleReference;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_address_type", nullable = false, length = 24)
    private AddressType pickupAddressType;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_address_type", nullable = false, length = 24)
    private AddressType deliveryAddressType;

    @Column(name = "scheduled_pickup_date")
    private Instant scheduledPickupDate;

    @Column(name = "actual_pickup_date")
    private Instant actualPickupDate;

    @Column(name = "scheduled_delivery_date")
    private Instant scheduledDeliveryDate;

    @Column(name = "actual_delivery_date")
    private Instant actualDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DispatchStatus status;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "dispatched_by", nullable = false, length = 64)
    private String dispatchedBy;

    @Column(name = "delivery_receipt_reference", length = 64)
    private String deliveryReceiptReference;

    @Column(name = "notes")
    private String notes;

    static TruckDispatch dispatch(
            ContainerAssignment assignment, UUID bookingId, MovementType movementType,
            String tdoReference, UUID driverId, UUID truckingVendorId, String vehicleReference,
            String pickupAddress, AddressType pickupAddressType,
            String deliveryAddress, AddressType deliveryAddressType,
            Instant scheduledPickup, Instant scheduledDelivery, Instant now, String actor
    ) {
        if (driverId == null && truckingVendorId == null) {
            throw new DomainRuleViolationException(
                    "Dispatch requires either an own-fleet driver or a trucking vendor");
        }
        if (truckingVendorId != null && (vehicleReference == null || vehicleReference.isBlank())) {
            throw new DomainRuleViolationException(
                    "A third-party vendor dispatch must record a vehicle reference for audit");
        }

        TruckDispatch dispatch = new TruckDispatch();
        dispatch.id = UUID.randomUUID();
        dispatch.assignment = assignment;
        dispatch.bookingId = bookingId;
        dispatch.movementType = movementType;
        dispatch.tdoReference = tdoReference;
        dispatch.driverId = driverId;
        dispatch.truckingVendorId = truckingVendorId;
        dispatch.vehicleReference = vehicleReference;
        dispatch.pickupAddress = pickupAddress;
        dispatch.pickupAddressType = pickupAddressType;
        dispatch.deliveryAddress = deliveryAddress;
        dispatch.deliveryAddressType = deliveryAddressType;
        dispatch.scheduledPickupDate = scheduledPickup;
        dispatch.scheduledDeliveryDate = scheduledDelivery;
        dispatch.status = DispatchStatus.DISPATCHED;
        dispatch.dispatchedAt = now;
        dispatch.dispatchedBy = actor;
        return dispatch;
    }

    void recordPickedUp(Instant at) {
        requireStatus(DispatchStatus.DISPATCHED, "pick-up");
        this.status = DispatchStatus.PICKED_UP;
        this.actualPickupDate = at;
    }

    void recordDelivered(Instant at, String receiptReference) {
        requireStatus(DispatchStatus.PICKED_UP, "delivery");
        this.status = DispatchStatus.DELIVERED;
        this.actualDeliveryDate = at;
        this.deliveryReceiptReference = receiptReference;
    }

    void recordFailed(String reason) {
        this.status = DispatchStatus.FAILED;
        this.notes = reason;
    }

    private void requireStatus(DispatchStatus expected, String action) {
        if (status != expected) {
            throw new DomainRuleViolationException(
                    "Cannot record " + action + " for " + tdoReference + " while it is " + status);
        }
    }
}
