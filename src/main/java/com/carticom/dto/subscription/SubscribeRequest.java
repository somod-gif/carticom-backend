package com.carticom.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest {

    @NotBlank(message = "planName is required")
    private String planName;

    private String provider;

    private String callbackUrl;
}
