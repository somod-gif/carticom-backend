package com.carticom.dto.businessowner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String fullName;
    private String phone;
    private String businessName;
    private String profileImageUrl;
}
