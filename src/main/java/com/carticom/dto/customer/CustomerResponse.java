package com.carticom.dto.customer;

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
public class CustomerResponse {
    private Long id;
    private String name;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private String status;
    private String avatarUrl;
    private LocalDateTime lastOrderDate;
    private String tags;
    private LocalDateTime createdAt;
}
