package com.carticom.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStoreResponse {
    private Long id;
    private String name;
    private String slug;
    private String category;
    private String sellerEmail;
    private LocalDateTime createdAt;
}
