package com.carticom.repository;

import com.carticom.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByStoreId(Long storeId);
    void deleteByStoreId(Long storeId);
}
