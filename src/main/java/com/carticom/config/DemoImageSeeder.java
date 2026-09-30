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
        // Give the demo store a real storefront template + brand palette so the
        // dashboard template picker and storefront theming have something to show.
        if (store.getTemplate() == null || store.getTemplate().isBlank()) {
            store.setTemplate("fashion-luxury");
            changed = true;
        }
        if (store.getPrimaryColor() == null || store.getPrimaryColor().isBlank()) {
            store.setPrimaryColor("#c9a84c");
            changed = true;
        }
        if (store.getSecondaryColor() == null || store.getSecondaryColor().isBlank()) {
            store.setSecondaryColor("#1a1a2e");
            changed = true;
        }
        if (store.getFontFamily() == null || store.getFontFamily().isBlank()) {
            store.setFontFamily("Inter");
            changed = true;
        }
        if (store.getDescription() == null || store.getDescription().isBlank()) {
            store.setDescription("Contemporary African fashion, hand-tailored in Lagos. "
                    + "Ankara, kente and leather pieces made to order.");
            changed = true;
        }
        if (store.getPhone() == null || store.getPhone().isBlank()) {
            store.setPhone("+234 802 000 0000");
            changed = true;
        }
        if (store.getEmail() == null || store.getEmail().isBlank()) {
            store.setEmail("hello@amakaslooks.cv");
            changed = true;
        }
        if (store.getAddress() == null || store.getAddress().isBlank()) {
            store.setAddress("14 Adeola Odeku Street, Victoria Island, Lagos");
            changed = true;
        }
        if (store.getCountry() == null || store.getCountry().isBlank()) {
            store.setCountry("Nigeria");
            changed = true;
        }
        if (store.getCurrency() == null || store.getCurrency().isBlank()) {
            store.setCurrency("NGN");
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
