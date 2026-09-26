package com.carticom.dto.admin;

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
public class AdminOrderResponse {
    private String orderNumber;
    private String storeName;
    private String customerName;
    private BigDecimal total;
    private String status;
    private String paymentStatus;
    private String channel;
    private LocalDateTime createdAt;
}
