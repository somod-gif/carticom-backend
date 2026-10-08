package com.carticom.service;

import com.carticom.dto.review.CreateReviewRequest;
import com.carticom.dto.review.ReviewListResponse;
import com.carticom.dto.review.ReviewResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Review;
import com.carticom.model.User;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.ReviewRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ReviewListResponse getApprovedReviews(Long productId) {
        List<ReviewResponse> reviews = reviewRepository
                .findByProductIdAndStatusOrderByCreatedAtDesc(productId, Review.STATUS_APPROVED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        Double average = reviewRepository.getAverageRating(productId, Review.STATUS_APPROVED);
        Long count = reviewRepository.getCount(productId, Review.STATUS_APPROVED);

        ReviewListResponse.Aggregate aggregate = ReviewListResponse.Aggregate.builder()
                .average(average != null ? Math.round(average * 10.0) / 10.0 : 0.0)
                .count(count != null ? count : 0L)
                .build();

        return ReviewListResponse.builder()
                .success(true)
                .data(reviews)
                .aggregate(aggregate)
                .build();
    }

    /**
     * Saves a new review as PENDING — it only appears on the product page after
     * a store owner approves it. One review per customer per product.
     */
    public ReviewResponse createReview(String userEmail, Long productId, CreateReviewRequest request) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new BadRequestException("Please choose a rating between 1 and 5 stars.");
        }
        if (request.getComment() != null && request.getComment().length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("Your review can be up to 1000 characters.");
        }
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (reviewRepository.existsByProductIdAndUserId(productId, userEmail)) {
            throw new BadRequestException("You've already reviewed this product");
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String comment = request.getComment() != null ? request.getComment().trim() : null;
        if (comment != null && comment.isBlank()) {
            comment = null;
        }

        Review review = Review.builder()
                .productId(productId)
                .userId(user.getEmail())
                .customerName(user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName()
                        : user.getEmail())
                .rating(request.getRating())
                .comment(comment)
                .status(Review.STATUS_PENDING)
                .build();

        reviewRepository.save(review);
        log.info("Review submitted for product {} by {}", productId, userEmail);

        return mapToResponse(review);
    }

    private ReviewResponse mapToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .customerName(review.getCustomerName())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
