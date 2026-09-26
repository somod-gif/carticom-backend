package com.carticom.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationResponse {
    private Long id;
    private String name;
    private String address;
    private Boolean isDefault;
    private Integer totalProducts;
    private LocalDateTime createdAt;
}
