package com.carticom.dto.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private String status;
    private String provider;
    private String trackingNumber;
    private String riderName;
    private String riderPhone;
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal deliveryFee;
    private String estimatedDeliveryTime;
    private String notes;
    private LocalDateTime createdAt;
}
