package com.carticom.dto.pos;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PosCheckoutRequest {

    @NotEmpty(message = "Cart must have items")
    private List<PosCartItem> items;

    private String paymentMethod;

    private String customerName;

    private String customerPhone;

    private BigDecimal discountAmount;
}
