package com.carticom.service;

import com.carticom.exception.ForbiddenException;
import com.carticom.model.Plan;
import com.carticom.model.Store;
import com.carticom.model.Subscription;
import com.carticom.model.SubscriptionStatus;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.PlanRepository;
import com.carticom.repository.ProductRepository;
import com.carticom.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanGuard {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StoreAccessService storeAccessService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public Plan effectivePlan(String sellerEmail) {
        return effectivePlan(storeAccessService.resolveStore(sellerEmail));
    }

    public Plan effectivePlan(Store store) {
        return subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() == null || s.getEndDate().isAfter(LocalDateTime.now()))
                .map(Subscription::getPlan)
                .orElseGet(() -> planRepository.findByName("FREE").orElse(null));
    }

    public void requireFeature(String sellerEmail, String feature) {
        Plan plan = effectivePlan(sellerEmail);
        if (plan == null) return;
        boolean allowed = switch (feature.toLowerCase(Locale.ROOT)) {
            case "analytics" -> Boolean.TRUE.equals(plan.getHasAnalytics());
            case "ai" -> Boolean.TRUE.equals(plan.getHasAiFeatures());
            case "support" -> Boolean.TRUE.equals(plan.getHasPrioritySupport());
            case "domain" -> Boolean.TRUE.equals(plan.getHasCustomDomain());
            case "branding" -> Boolean.TRUE.equals(plan.getHasRemoveBranding());
            default -> false;
        };
        if (!allowed) {
            String needed = "FREE".equals(plan.getName()) ? "Pro" : "Enterprise";
            log.info("Plan gate: {} blocked for feature '{}' on plan {}",
                    sellerEmail, feature, plan.getName());
            throw new ForbiddenException(
                    featureLabel(feature) + " is a " + needed + " feature. Upgrade your plan to unlock it.");
        }
    }

    public void requireProductCapacity(String sellerEmail) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        Plan plan = effectivePlan(store);
        if (plan == null || plan.getMaxProducts() == null) return;
        long count = productRepository.findByStoreId(store.getId()).size();
        if (count >= plan.getMaxProducts()) {
            throw upgradeRequired(plan, "products", plan.getMaxProducts(), count);
        }
    }

    public void requireOrderCapacity(String sellerEmail) {
        requireOrderCapacity(storeAccessService.resolveStore(sellerEmail));
    }

    public void requireOrderCapacity(Store store) {
        Plan plan = effectivePlan(store);
        if (plan == null || plan.getMaxOrdersPerMonth() == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        long count = orderRepository.findByStoreIdAndCreatedAtBetween(store.getId(), monthStart, now).size();
        if (count >= plan.getMaxOrdersPerMonth()) {
            throw upgradeRequired(plan, "orders this month", plan.getMaxOrdersPerMonth(), count);
        }
    }

    public void requireCustomerCapacity(String sellerEmail) {
        requireCustomerCapacity(storeAccessService.resolveStore(sellerEmail));
    }

    public void requireCustomerCapacity(Store store) {
        Plan plan = effectivePlan(store);
        if (plan == null || plan.getMaxCustomers() == null) return;
        long count = customerRepository.countCustomers(store.getId());
        if (count >= plan.getMaxCustomers()) {
            throw upgradeRequired(plan, "customers", plan.getMaxCustomers(), count);
        }
    }

    private ForbiddenException upgradeRequired(Plan plan, String noun, long cap, long count) {
        String needed = "FREE".equals(plan.getName()) ? "Pro" : "Enterprise";
        log.info("Plan gate: cap reached plan={} cap={} count={} noun={}", plan.getName(), cap, count, noun);
        return new ForbiddenException(
                "Your " + plan.getName() + " plan allows up to " + cap + " " + noun
                        + " (" + count + "/" + cap + "). Upgrade to " + needed + " to continue.");
    }

    private String featureLabel(String feature) {
        return switch (feature.toLowerCase(Locale.ROOT)) {
            case "analytics" -> "Analytics";
            case "ai" -> "AI Business Advisor";
            case "support" -> "Priority support";
            case "domain" -> "Custom domains";
            case "branding" -> "Removing Carticom branding";
            default -> feature;
        };
    }
}
