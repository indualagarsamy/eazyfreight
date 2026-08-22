package com.eazyfreight.booking;

import jakarta.validation.constraints.NotBlank;

public record GenerateTruckDeliveryOrderRequest(
        @NotBlank(message = "deliveryAddress is required") String deliveryAddress
) {
}
