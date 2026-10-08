package com.carticom.dto.returnrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnResponse {
    private Long id;
    private Long orderId;
    private String customerId;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
