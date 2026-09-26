package com.carticom.repository;

import com.carticom.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStoreId(Long storeId);

    List<Product> findByStoreIdAndIsActive(Long storeId, Boolean isActive);

    List<Product> findByStoreIdAndCategory(Long storeId, String category);

    List<Product> findByStoreIdAndIsFeatured(Long storeId, Boolean isFeatured);

    List<Product> findByStoreIdAndStockQuantityLessThan(Long storeId, Integer threshold);

    @Query("SELECT SUM(p.price * p.soldCount) FROM Product p WHERE p.store.id = ?1")
    BigDecimal getTotalRevenue(Long storeId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.store.id = ?1 AND p.isActive = true")
    Long countActiveProducts(Long storeId);

    boolean existsByStoreIdAndSku(Long storeId, String sku);

    boolean existsByStoreIdAndBarcode(Long storeId, String barcode);
}
