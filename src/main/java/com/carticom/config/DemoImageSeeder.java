package com.carticom.config;

import com.carticom.model.Product;
import com.carticom.model.Store;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(21)
@ConditionalOnProperty(name = "seeder.enabled", havingValue = "true")
public class DemoImageSeeder implements CommandLineRunner {

    private static final Map<String, String> PRODUCT_IMAGES = Map.ofEntries(
            Map.entry("Ankara Wrap Dress", "/image/download.jpg"),
            Map.entry("Kente Tote Bag", "/image/download2.jpg"),
            Map.entry("Leather Slide Sandals", "/image/sneakers.jpg"),
            Map.entry("Silk Headwrap", "/image/wig.jpg"),
            Map.entry("Denim Trucker Jacket", "/image/download2.jpg"),
            Map.entry("Beaded Necklace", "/image/skincare.jpg"),
            Map.entry("Cotton Kaftan", "/image/download.jpg"),
            Map.entry("Raffia Espadrilles", "/image/sneakers.jpg"),
            Map.entry("Wireless Earbuds Pro", "/image/earbuds.jpg"),
            Map.entry("Vitamin C Glow Serum", "/image/skincare.jpg"),
            Map.entry("Air Runner Sneakers", "/image/sneakers.jpg")
    );

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        storeRepository.findAll().forEach(s -> {
            if (s.getStatus() == null || s.getStatus().isBlank()) {
                s.setStatus("ACTIVE");
                storeRepository.save(s);
            }
        });

        Store store = storeRepository.findBySlug("amakas-looks").orElse(null);
        if (store == null) {
            log.info("Image seeder: demo store not found - skipping");
            return;
        }

        boolean changed = false;
        if (store.getLogoUrl() == null || store.getLogoUrl().isBlank()) {
            store.setLogoUrl("/image/carticom_logo.png");
            changed = true;
        }
        if (store.getBannerUrl() == null || store.getBannerUrl().isBlank()) {
            store.setBannerUrl("/image/download.jpg");
            changed = true;
        }
        if (changed) {
            storeRepository.save(store);
        }

        List<Product> products = productRepository.findByStoreId(store.getId());
        for (Product product : products) {
            if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                continue;
            }
            String image = PRODUCT_IMAGES.get(product.getName());
            if (image != null) {
                product.setImageUrl(image);
                productRepository.save(product);
            }
        }
        log.info("Image seeder: ensured logo/banner and product images for {}", store.getSlug());
    }
}
