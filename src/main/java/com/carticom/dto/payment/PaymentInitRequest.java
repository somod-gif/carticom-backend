package com.carticom.dto.payment;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "100", message = "Minimum amount is 100")
    private BigDecimal amount;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Provider is required")
    private String provider;

    private Long orderId;

    private String callbackUrl;
}
