package com.carticom.service;

import com.carticom.dto.pos.*;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {

    private final PosTransactionRepository posTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final PlanGuard planGuard;
    private final NotificationService notificationService;

    @Transactional
    public PosCheckoutResponse checkout(String sellerEmail, PosCheckoutRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        planGuard.requireOrderCapacity(sellerEmail);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (PosCartItem cartItem : request.getItems()) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .filter(p -> p.getStore().getId().equals(store.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + cartItem.getProductId()));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + product.getName());
            }

            BigDecimal price = cartItem.getCustomPrice() != null ? cartItem.getCustomPrice() : product.getPrice();
            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            product.setSoldCount(product.getSoldCount() + cartItem.getQuantity());
            productRepository.save(product);
            notificationService.checkLowStock(store.getId(), product);

            OrderItem orderItem = OrderItem.builder()
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(price)
                    .totalPrice(itemTotal)
                    .product(product)
                    .build();
            orderItems.add(orderItem);
        }

        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount = totalAmount.subtract(discount);

        Order order = Order.builder()
                .orderNumber("POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .store(store)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .subtotal(totalAmount)
                .taxAmount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(finalAmount)
                .channel(OrderChannel.POS)
                .build();

        orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        orderItemRepository.saveAll(orderItems);


        String receiptNumber = "RCP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        PosTransaction transaction = PosTransaction.builder()
                .store(store)
                .order(order)
                .receiptNumber(receiptNumber)
                .totalAmount(finalAmount)
                .paymentMethod(request.getPaymentMethod() != null ?
                        PaymentMethod.valueOf(request.getPaymentMethod()) : PaymentMethod.CASH_ON_DELIVERY)
                .paymentStatus(PaymentStatus.PAID)
                .customerName(request.getCustomerName())
                .build();

        posTransactionRepository.save(transaction);

        notificationService.notifyStoreTeam(store, "order",
                "In-store sale " + order.getOrderNumber(),
                "POS receipt " + receiptNumber + " — " + finalAmount.toPlainString() + " NGN");

        List<PosReceiptItem> receiptItems = orderItems.stream()
                .map(item -> PosReceiptItem.builder()
                        .name(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        log.info("POS checkout: {} for store {}", receiptNumber, store.getName());

        return PosCheckoutResponse.builder()
                .receiptNumber(receiptNumber)
                .orderId(order.getId())
                .totalAmount(totalAmount)
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .paymentMethod(transaction.getPaymentMethod().name())
                .paymentStatus("PAID")
                .items(receiptItems)
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    public List<PosTransaction> getTransactions(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return posTransactionRepository.findByStoreIdOrderByCreatedAtDesc(store.getId());
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }
}
