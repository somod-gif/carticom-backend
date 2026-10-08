package com.carticom.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Envelope for the public reviews list: same success/data shape as
 * SuccessResponse, plus the rating totals the storefront shows above the list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewListResponse {
    private boolean success;
    private List<ReviewResponse> data;
    private Aggregate aggregate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Aggregate {
        private Double average;
        private Long count;
    }
}
