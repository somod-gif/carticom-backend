package com.carticom.service;

import com.carticom.dto.payment.PaymentInitResponse;
import com.carticom.dto.subscription.PlanResponse;
import com.carticom.dto.subscription.SubscribeRequest;
import com.carticom.dto.subscription.SubscriptionResponse;
import com.carticom.dto.subscription.UpgradeResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ForbiddenException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public List<PlanResponse> getAllPlans() {
        return planRepository.findAll().stream().map(this::mapPlanToResponse).collect(Collectors.toList());
    }

    @Transactional
    public UpgradeResponse startUpgrade(String sellerEmail, SubscribeRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        Plan plan = planRepository.findByName(resolvePlanName(request.getPlanName()))
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.getPlanName()));

        Subscription current = activeSubscription(store).orElse(null);
        if (current != null && current.getPlan().getId().equals(plan.getId())) {
            throw new BadRequestException("You are already on the " + plan.getName() + " plan");
        }

        // FREE plan: activate immediately (first subscribe or downgrade)
        if (plan.getPrice() == null || plan.getPrice().signum() == 0) {
            cancelPendingSubscriptions(store);
            if (current != null) {
                current.setStatus(SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(current);
            }
            Subscription free = Subscription.builder()
                    .store(store).plan(plan).status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDateTime.now()).endDate(null)
                    .build();
            subscriptionRepository.save(free);
            log.info("Store {} switched to FREE plan", store.getSlug());
            return UpgradeResponse.builder()
                    .planName(plan.getName())
                    .amount(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .message("You are now on the Free plan")
                    .build();
        }

        // Paid plan: create PENDING subscription, initialize gateway checkout
        cancelPendingSubscriptions(store);
        Subscription pending = Subscription.builder()
                .store(store).plan(plan).status(SubscriptionStatus.PENDING)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusMonths(1))
                .build();
        subscriptionRepository.save(pending);

        String provider = (request.getProvider() == null || request.getProvider().isBlank())
                ? "paystack" : request.getProvider();

        PaymentInitResponse init;
        try {
            init = paymentService.initializeForSubscription(pending, provider, sellerEmail, request.getCallbackUrl());
        } catch (RuntimeException e) {
            pending.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(pending);
            throw e;
        }

        return UpgradeResponse.builder()
                .planName(plan.getName())
                .amount(plan.getPrice())
                .status("PENDING")
                .reference(init.getReference())
                .authorizationUrl(init.getAuthorizationUrl())
                .provider(init.getProvider())
                .message("Complete payment to activate " + plan.getName())
                .build();
    }

    @Transactional
    public UpgradeResponse verifyUpgrade(String sellerEmail, String reference) {
        Payment payment = paymentRepository.findByReference(reference)
                .or(() -> paymentRepository.findByGatewayReference(reference))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        Subscription subscription = payment.getSubscription();
        if (subscription == null) {
            throw new BadRequestException("Not a subscription payment");
        }
        if (!subscription.getStore().getSeller().getEmail().equals(sellerEmail)) {
            throw new ForbiddenException("This payment belongs to another store");
        }

        paymentService.verifyAndSettle(reference);

        Subscription refreshed = subscriptionRepository.findById(subscription.getId()).orElse(subscription);
        boolean paid = payment.getStatus() == PaymentStatus.PAID;
        String status = paid ? SubscriptionStatus.ACTIVE.name() : payment.getStatus().name();
        return UpgradeResponse.builder()
                .planName(refreshed.getPlan().getName())
                .amount(payment.getAmount())
                .status(status)
                .reference(payment.getReference())
                .provider(payment.getMethod().name().toLowerCase())
                .message(paid ? refreshed.getPlan().getName() + " plan activated" : "Payment not completed yet")
                .build();
    }

    public SubscriptionResponse getCurrentSubscription(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        Subscription sub = activeSubscription(store).orElseGet(() -> {
            Plan freePlan = planRepository.findByName("FREE").orElse(null);
            if (freePlan == null) return null;
            return Subscription.builder().store(store).plan(freePlan)
                    .status(SubscriptionStatus.ACTIVE).startDate(store.getCreatedAt()).build();
        });
        if (sub == null) throw new ResourceNotFoundException("No subscription");
        return mapSubscriptionToResponse(sub);
    }

    public boolean hasFeatureAccess(String sellerEmail, String feature) {
        Store store = getStoreBySeller(sellerEmail);
        Plan plan = effectivePlan(store);
        if (plan == null) return false;
        return switch (feature.toLowerCase()) {
            case "analytics" -> Boolean.TRUE.equals(plan.getHasAnalytics());
            case "ai" -> Boolean.TRUE.equals(plan.getHasAiFeatures());
            case "support" -> Boolean.TRUE.equals(plan.getHasPrioritySupport());
            case "domain" -> Boolean.TRUE.equals(plan.getHasCustomDomain());
            case "branding" -> Boolean.TRUE.equals(plan.getHasRemoveBranding());
            default -> false;
        };
    }

    public boolean checkProductLimit(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        Plan plan = effectivePlan(store);
        if (plan == null) return true;
        long count = productRepository.findByStoreId(store.getId()).size();
        return count < plan.getMaxProducts();
    }

    public Plan effectivePlan(Store store) {
        return activeSubscription(store).map(Subscription::getPlan)
                .orElseGet(() -> planRepository.findByName("FREE").orElse(null));
    }

    private Optional<Subscription> activeSubscription(Store store) {
        return subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() == null || s.getEndDate().isAfter(LocalDateTime.now()));
    }

    private void cancelPendingSubscriptions(Store store) {
        subscriptionRepository.findByStoreIdAndStatus(store.getId(), SubscriptionStatus.PENDING)
                .ifPresent(pending -> {
                    pending.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(pending);
                });
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private String marketingName(String name) {
        if (name == null) return null;
        return switch (name) {
            case "FREE" -> "Starter";
            case "PRO" -> "Growth";
            case "ENTERPRISE" -> "Enterprise";
            default -> name;
        };
    }

    private String planDescription(String name) {
        if (name == null) return "";
        return switch (name) {
            case "FREE" -> "For getting your first store live.";
            case "PRO" -> "For sellers ready to make it a system.";
            default -> "For teams building serious momentum.";
        };
    }

    private String resolvePlanName(String raw) {
        String n = raw == null ? "" : raw.trim().toUpperCase();
        return switch (n) {
            case "STARTER" -> "FREE";
            case "GROWTH", "BUSINESS" -> "PRO";
            default -> n;
        };
    }

    private PlanResponse mapPlanToResponse(Plan plan) {
        BigDecimal price = plan.getPrice();
        int staffLimit = "FREE".equals(plan.getName()) ? 1 : "PRO".equals(plan.getName()) ? 3 : 10;
        return PlanResponse.builder()
                .id(plan.getId()).name(marketingName(plan.getName())).price(price)
                .monthlyPrice(price)
                .yearlyPrice(price == null ? null : price.multiply(BigDecimal.TEN))
                .description(planDescription(plan.getName()))
                .productLimit(plan.getMaxProducts())
                .staffLimit(staffLimit)
                .paymentsEnabled(true)
                .customDomainEnabled(Boolean.TRUE.equals(plan.getHasCustomDomain()))
                .durationDays(30)
                .maxProducts(plan.getMaxProducts()).maxOrdersPerMonth(plan.getMaxOrdersPerMonth())
                .maxCustomers(plan.getMaxCustomers()).hasAnalytics(plan.getHasAnalytics())
                .hasAiFeatures(plan.getHasAiFeatures()).hasPrioritySupport(plan.getHasPrioritySupport())
                .hasCustomDomain(plan.getHasCustomDomain()).hasRemoveBranding(plan.getHasRemoveBranding())
                .build();
    }

    private SubscriptionResponse mapSubscriptionToResponse(Subscription sub) {
        Long storeId = sub.getStore().getId();
        int productCount = productRepository.findByStoreId(storeId).size();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        int ordersThisMonth = orderRepository.findByStoreIdAndCreatedAtBetween(storeId, monthStart, now).size();
        return SubscriptionResponse.builder()
                .id(sub.getId()).storeName(sub.getStore().getName())
                .planName(sub.getPlan().getName()).status(sub.getStatus().name())
                .startDate(sub.getStartDate()).endDate(sub.getEndDate())
                .productCount(productCount).orderCountThisMonth(ordersThisMonth)
                .maxProducts(sub.getPlan().getMaxProducts())
                .maxOrdersPerMonth(sub.getPlan().getMaxOrdersPerMonth())
                .build();
    }
}
