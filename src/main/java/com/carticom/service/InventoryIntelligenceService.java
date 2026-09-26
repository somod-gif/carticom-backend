package com.carticom.service;

import com.carticom.model.Product;
import com.carticom.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryIntelligenceService {

    private final ProductRepository productRepository;

    public List<Map<String, Object>> getInventoryHealth(Long storeId) {
        List<Product> products = productRepository.findByStoreId(storeId);

        return products.stream().map(product -> {
            Map<String, Object> health = new HashMap<>();
            health.put("productId", product.getId());
            health.put("productName", product.getName());
            health.put("currentStock", product.getStockQuantity());
            health.put("lowStockThreshold", product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5);
            health.put("soldCount", product.getSoldCount());

            int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            int threshold = product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 5;
            int sold = product.getSoldCount() != null ? product.getSoldCount() : 0;

            double salesVelocity = sold / 30.0;
            health.put("salesVelocity", Math.round(salesVelocity * 100.0) / 100.0);

            if (salesVelocity > 0) {
                int daysUntilStockout = (int) Math.ceil(stock / salesVelocity);
                health.put("daysUntilStockout", daysUntilStockout);
                health.put("stockoutDate", LocalDateTime.now().plusDays(daysUntilStockout).toLocalDate().toString());
            } else {
                health.put("daysUntilStockout", -1);
                health.put("stockoutDate", null);
            }

            boolean isLowStock = stock <= threshold;
            boolean isOutOfStock = stock == 0;
            boolean isOverstocked = stock > threshold * 10 && salesVelocity < 0.1;

            String status;
            if (isOutOfStock) status = "OUT_OF_STOCK";
            else if (isLowStock) status = "LOW_STOCK";
            else if (isOverstocked) status = "OVERSTOCKED";
            else status = "HEALTHY";

            health.put("status", status);

            int recommendedReorder = isLowStock ? (int) Math.ceil(salesVelocity * 30) - stock : 0;
            health.put("recommendedReorder", Math.max(0, recommendedReorder));

            return health;
        }).collect(Collectors.toList());
    }
}
