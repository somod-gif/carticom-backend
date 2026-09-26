package com.carticom.service;

import com.carticom.dto.subscription.PlanResponse;
import com.carticom.dto.subscription.SubscriptionResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public List<PlanResponse> getAllPlans() {
        return planRepository.findAll().stream().map(this::mapPlanToResponse).collect(Collectors.toList());
    }

    @Transactional
    public SubscriptionResponse subscribe(String sellerEmail, String planName) {
        Store store = getStoreBySeller(sellerEmail);
        Plan plan = planRepository.findByName(planName.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planName));

        subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE)
                .ifPresent(s -> { throw new BadRequestException("Already subscribed"); });

        Subscription sub = Subscription.builder()
                .store(store).plan(plan).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusMonths(1))
                .build();
        subscriptionRepository.save(sub);
        return mapSubscriptionToResponse(sub);
    }

    public SubscriptionResponse getCurrentSubscription(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        Subscription sub = subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE)
                .orElseGet(() -> {
                    Plan freePlan = planRepository.findByName("FREE").orElse(null);
                    if (freePlan == null) return null;
                    return Subscription.builder().store(store).plan(freePlan)
                            .status(SubscriptionStatus.ACTIVE).startDate(store.getCreatedAt()).build();
                });
        if (sub == null) throw new ResourceNotFoundException("No subscription");
        return mapSubscriptionToResponse(sub);
    }

    @Transactional
    public SubscriptionResponse changePlan(String sellerEmail, String planName) {
        Store store = getStoreBySeller(sellerEmail);
        Plan newPlan = planRepository.findByName(planName.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(current);
                });

        Subscription sub = Subscription.builder()
                .store(store).plan(newPlan).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusMonths(1))
                .build();
        subscriptionRepository.save(sub);
        return mapSubscriptionToResponse(sub);
    }

    public boolean hasFeatureAccess(String sellerEmail, String feature) {
        Store store = getStoreBySeller(sellerEmail);
        Subscription sub = subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE).orElse(null);
        if (sub == null) return false;
        Plan plan = sub.getPlan();
        return switch (feature.toLowerCase()) {
            case "analytics" -> plan.getHasAnalytics();
            case "ai" -> plan.getHasAiFeatures();
            case "support" -> plan.getHasPrioritySupport();
            case "domain" -> plan.getHasCustomDomain();
            case "branding" -> plan.getHasRemoveBranding();
            default -> false;
        };
    }

    public boolean checkProductLimit(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        Subscription sub = subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE).orElse(null);
        if (sub == null) return true;
        long count = productRepository.findByStoreId(store.getId()).size();
        return count < sub.getPlan().getMaxProducts();
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private PlanResponse mapPlanToResponse(Plan plan) {
        return PlanResponse.builder()
                .id(plan.getId()).name(plan.getName()).price(plan.getPrice())
                .maxProducts(plan.getMaxProducts()).maxOrdersPerMonth(plan.getMaxOrdersPerMonth())
                .maxCustomers(plan.getMaxCustomers()).hasAnalytics(plan.getHasAnalytics())
                .hasAiFeatures(plan.getHasAiFeatures()).hasPrioritySupport(plan.getHasPrioritySupport())
                .hasCustomDomain(plan.getHasCustomDomain()).hasRemoveBranding(plan.getHasRemoveBranding())
                .build();
    }

    private SubscriptionResponse mapSubscriptionToResponse(Subscription sub) {
        int productCount = productRepository.findByStoreId(sub.getStore().getId()).size();
        return SubscriptionResponse.builder()
                .id(sub.getId()).storeName(sub.getStore().getName())
                .planName(sub.getPlan().getName()).status(sub.getStatus().name())
                .startDate(sub.getStartDate()).endDate(sub.getEndDate())
                .productCount(productCount).orderCountThisMonth(0)
                .maxProducts(sub.getPlan().getMaxProducts())
                .maxOrdersPerMonth(sub.getPlan().getMaxOrdersPerMonth())
                .build();
    }
}