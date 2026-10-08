package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.dto.review.CreateReviewRequest;
import com.carticom.dto.review.ReviewListResponse;
import com.carticom.dto.review.ReviewResponse;
import com.carticom.exception.ForbiddenException;
import com.carticom.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Reviews", description = "Customer reviews and ratings for products")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Get approved reviews for a product",
            description = "Public. Returns approved reviews, newest first, plus the average rating and total count.")
    public ResponseEntity<ReviewListResponse> getReviews(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getApprovedReviews(id));
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Leave a review for a product",
            description = "Requires sign in. One review per customer per product. New reviews wait for approval before they appear.")
    public ResponseEntity<SuccessResponse<ReviewResponse>> createReview(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewRequest request) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ForbiddenException("Sign in to leave a review");
        }
        ReviewResponse response = reviewService.createReview(authentication.getName(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>(true, response));
    }
}
