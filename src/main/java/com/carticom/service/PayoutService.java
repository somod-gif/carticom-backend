package com.carticom.service;

import com.carticom.dto.payout.PayoutSummaryResponse;
import com.carticom.dto.payout.PayoutTransactionResponse;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Order;
import com.carticom.model.Payment;
import com.carticom.model.PaymentStatus;
import com.carticom.model.Store;
import com.carticom.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    /** How many recent transactions the payout summary reports. */
    private static final int RECENT_TRANSACTIONS_LIMIT = 10;

    private final StoreAccessService storeAccessService;
    private final PaymentRepository paymentRepository;

    /**
     * Payout summary for one of the caller's stores, aggregated from real
     * payment records only - nothing is estimated or fabricated. A payment
     * counts as paid out when its status is {@link PaymentStatus#PAID}, the
     * state {@link PaymentService} settles a payment to after the payment
     * gateway has confirmed the money arrived and the amount matches.
     *
     * <p>Only order payments are included: the query joins through
     * {@code payment.order.store}, so subscription payments (the seller paying
     * Carticom) are naturally excluded.</p>
     */
    public PayoutSummaryResponse getPayoutSummary(String sellerEmail, Long storeId) {
        Store store = resolveOwnedStore(sellerEmail, storeId);

        List<Payment> paidPayments = paymentRepository
                .findByOrderStoreIdAndStatus(store.getId(), PaymentStatus.PAID)
                .stream()
                .sorted(Comparator.comparing(PayoutService::paidAt).reversed())
                .toList();

        BigDecimal totalPaidOut = paidPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime firstPayoutDate = paidPayments.isEmpty()
                ? null
                : paidAt(paidPayments.get(paidPayments.size() - 1));
        LocalDateTime lastPayoutDate = paidPayments.isEmpty()
                ? null
                : paidAt(paidPayments.get(0));

        List<PayoutTransactionResponse> recentTransactions = paidPayments.stream()
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .map(payment -> toTransaction(payment, store.getCurrency()))
                .toList();

        return PayoutSummaryResponse.builder()
                .totalPaidOut(totalPaidOut)
                .currency(store.getCurrency())
                .transactionCount((long) paidPayments.size())
                .firstPayoutDate(firstPayoutDate)
                .lastPayoutDate(lastPayoutDate)
                .recentTransactions(recentTransactions)
                .build();
    }

    /**
     * When the money for this payment arrived. The Payment entity has no
     * dedicated paidAt column; its {@code updatedAt} is refreshed when the
     * payment is settled to PAID - the only write after creation - so for a
     * paid payment {@code updatedAt} is the settlement time.
     */
    private static LocalDateTime paidAt(Payment payment) {
        return payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
    }

    private PayoutTransactionResponse toTransaction(Payment payment, String currency) {
        Order order = payment.getOrder();
        return PayoutTransactionResponse.builder()
                .id(payment.getId())
                .orderId(order != null ? order.getId() : null)
                .amount(payment.getAmount())
                .currency(currency)
                .status(payment.getStatus().name())
                .paidAt(paidAt(payment))
                .build();
    }

    /** Same ownership check as StoreService: 404 when the store is not the caller's. */
    private Store resolveOwnedStore(String sellerEmail, Long storeId) {
        Store store = storeAccessService.resolveStore(sellerEmail);
        if (!store.getId().equals(storeId)) {
            throw new ResourceNotFoundException("Store not found");
        }
        return store;
    }
}
