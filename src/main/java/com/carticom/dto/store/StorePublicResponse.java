package com.carticom.dto.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorePublicResponse {
    private Long id;
    private String name;
    private String slug;
    private String category;
    private String theme;
    private String layout;
    private Integer productCount;
    private LocalDateTime createdAt;
}
