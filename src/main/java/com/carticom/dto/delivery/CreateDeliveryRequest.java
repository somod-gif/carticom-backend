package com.carticom.dto.delivery;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryRequest {

    @NotBlank(message = "Order ID is required")
    private Long orderId;

    private String provider;

    private String pickupAddress;

    private String dropoffAddress;

    private BigDecimal deliveryFee;

    private String notes;
}
