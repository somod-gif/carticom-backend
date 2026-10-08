package com.carticom.dto.review;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotNull(message = "Please choose a rating")
    @Min(value = 1, message = "Please choose a rating between 1 and 5 stars")
    @Max(value = 5, message = "Please choose a rating between 1 and 5 stars")
    private Integer rating;

    @JsonAlias("content")
    @Size(max = 1000, message = "Your review can be up to 1000 characters")
    private String comment;
}
