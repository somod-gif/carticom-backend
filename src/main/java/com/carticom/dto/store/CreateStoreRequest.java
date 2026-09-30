package com.carticom.dto.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoreRequest {

    private String name;

    private String category;

    private String storeName;

    private String storeSlug;

    private String phone;

    private String description;

    private String businessCategory;

    private String email;

    private String address;

    private String country;

    private String currency;

    public String resolveName() {
        if (name != null && !name.isBlank()) return name;
        return storeName;
    }

    public String resolveCategory() {
        if (category != null && !category.isBlank()) return category;
        return businessCategory;
    }
}
