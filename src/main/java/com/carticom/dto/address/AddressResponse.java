package com.carticom.dto.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    private Long id;
    private String label;
    private String street;
    private String city;
    private String state;
    private String country;
    private String phone;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
