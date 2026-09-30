package com.carticom.dto.businessowner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryDTO {
    private Long id;
    private String orderId;
    private String customerName;
    private String customerEmail;
    private Map<String, Object> customer;
    private BigDecimal total;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Integer items;
    private String createdAt;
    private String date;
}
