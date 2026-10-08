package com.carticom.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String customerName;
    private Integer rating;
    private String comment;
    private String status;
    private LocalDateTime createdAt;
}
