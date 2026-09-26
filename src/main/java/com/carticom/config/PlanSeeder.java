package com.carticom.config;

import com.carticom.model.Plan;
import com.carticom.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;

    @Override
    public void run(String... args) {
        seed("FREE", "0.00", 10, 50, 100, false, false, false, false, false,
                "{\"description\": \"Perfect for getting started\"}");
        seed("PRO", "5000.00", 100, 500, 5000, true, true, true, false, false,
                "{\"description\": \"For growing businesses\"}");
        seed("ENTERPRISE", "25000.00", 999999, 999999, 999999, true, true, true, true, true,
                "{\"description\": \"For established businesses\"}");
    }

    private void seed(String name, String price, int maxProducts, int maxOrders, int maxCustomers,
                      boolean analytics, boolean ai, boolean priority, boolean domain, boolean noBranding,
                      String featuresJson) {
        if (planRepository.findByName(name).isPresent()) {
            return;
        }
        planRepository.save(Plan.builder()
                .name(name)
                .price(new BigDecimal(price))
                .maxProducts(maxProducts)
                .maxOrdersPerMonth(maxOrders)
                .maxCustomers(maxCustomers)
                .hasAnalytics(analytics)
                .hasAiFeatures(ai)
                .hasPrioritySupport(priority)
                .hasCustomDomain(domain)
                .hasRemoveBranding(noBranding)
                .featuresJson(featuresJson)
                .build());
        log.info("Seeded plan: {}", name);
    }
}
