package com.carticom.config;

import com.carticom.model.*;
import com.carticom.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seeder.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();

        if (userRepository.findByEmail("vendor@carticom.cv").isPresent()) {
            log.info("Seeder: demo data already present - skipping");
            return;
        }

        User vendor = user("Amaka Okafor", "vendor@carticom.cv", "vendor12345", Role.VENDOR);
        User staff = user("Tunde Bello", "staff@carticom.cv", "staff12345", Role.STAFF);
        User customerUser = user("Chidera Nwosu", "customer@carticom.cv", "customer12345", Role.CUSTOMER);

        Store store = storeRepository.save(Store.builder()
                .name("Amaka's Looks")
                .slug("amakas-looks")
                .category("Fashion")
                .seller(vendor)
                .build());

        storeMemberRepository.save(StoreMember.builder().store(store).user(staff).build());

        List<Product> products = productRepository.saveAll(List.of(
                product(store, "Ankara Wrap Dress", "Bold wax-print wrap dress, hand-stitched in Lagos", new BigDecimal("18500"), 40, "ANK-WRAP-01", "Dresses", true, true),
                product(store, "Kente Tote Bag", "Handwoven kente tote with leather straps", new BigDecimal("12000"), 25, "KNT-TOTE-02", "Bags", true, false),
                product(store, "Leather Slide Sandals", "Genuine leather slides, sizes 36-44", new BigDecimal("9500"), 60, "LTH-SLIDE-03", "Footwear", true, false),
                product(store, "Silk Headwrap", "Pre-tied silk headwrap, 12 colours", new BigDecimal("4500"), 80, "SLK-WRAP-04", "Accessories", true, true),
                product(store, "Denim Trucker Jacket", "Washed denim jacket, unisex", new BigDecimal("25000"), 15, "DNM-JKT-05", "Outerwear", true, false),
                product(store, "Beaded Necklace", "Hand-beaded statement necklace", new BigDecimal("7500"), 35, "BEAD-NCK-06", "Jewellery", true, false),
                product(store, "Cotton Kaftan", "Breathable cotton kaftan, embroidered collar", new BigDecimal("15000"), 30, "CTN-KFT-07", "Men", true, false),
                product(store, "Raffia Espadrilles", "Woven raffia espadrilles with jute sole", new BigDecimal("11000"), 20, "RAF-ESP-08", "Footwear", false, false)
        ));

        Customer guestAda = customer(store, "Ada Eze", "ada@example.com", "08031234567");
        Customer guestChidi = customer(store, "Chidi Okonkwo", "chidi@example.com", "08057654321");
        Customer registered = customer(store, "Chidera Nwosu", "customer@carticom.cv", "08061112222");
        customerUser.setPhone("08061112222");
        userRepository.save(customerUser);

        Product p1 = products.get(0);
        Product p2 = products.get(1);
        Product p3 = products.get(3);
        Product p4 = products.get(2);

        Order order1 = order(store, guestAda, OrderStatus.DELIVERED, PaymentStatus.PAID,
                items(p1, 1, p3, 2), "12 Admiralty Way, Lekki, Lagos", "08031234567");
        orderRepository.save(order1);
        paymentRepository.save(Payment.builder()
                .order(order1).method(PaymentMethod.PAYSTACK).status(PaymentStatus.PAID)
                .amount(order1.getTotal()).reference("CART-SEED000001")
                .gatewayResponse("Approved (seeded)")
                .build());

        Order order2 = order(store, registered, OrderStatus.SHIPPED, PaymentStatus.PAID,
                items(p2, 1, p4, 1), "5 Zoo Road, Kano", "08061112222");
        orderRepository.save(order2);
        paymentRepository.save(Payment.builder()
                .order(order2).method(PaymentMethod.NOMBA).status(PaymentStatus.PAID)
                .amount(order2.getTotal()).reference("CART-SEED000002")
                .gatewayResponse("SUCCESS (seeded)")
                .build());

        Order order3 = order(store, guestChidi, OrderStatus.PENDING, PaymentStatus.PENDING,
                items(p3, 3), "8 Danfo Street, Yaba, Lagos", "08057654321");
        orderRepository.save(order3);
        paymentRepository.save(Payment.builder()
                .order(order3).method(PaymentMethod.PAYSTACK).status(PaymentStatus.PENDING)
                .amount(order3.getTotal()).reference("CART-SEED000003")
                .build());

        Order order4 = order(store, registered, OrderStatus.CONFIRMED, PaymentStatus.PAID,
                items(p1, 2), "5 Zoo Road, Kano", "08061112222");
        orderRepository.save(order4);
        paymentRepository.save(Payment.builder()
                .order(order4).method(PaymentMethod.PAYSTACK).status(PaymentStatus.PAID)
                .amount(order4.getTotal()).reference("CART-SEED000004")
                .gatewayResponse("Approved (seeded)")
                .build());

        log.info("Seeder: demo data ready - vendor@carticom.cv / vendor12345, staff@carticom.cv / staff12345, " +
                "customer@carticom.cv / customer12345, store slug 'amakas-looks'");
    }

    private void seedAdmin() {
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .fullName("Carticom Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build());
        log.info("Seeder: admin account created ({})", adminEmail);
    }

    private User user(String name, String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
                .fullName(name)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
    }

    private Product product(Store store, String name, String description, BigDecimal price, int stock,
                            String sku, String category, boolean active, boolean featured) {
        return Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stockQuantity(stock)
                .sku(sku)
                .category(category)
                .isActive(active)
                .isFeatured(featured)
                .weight(BigDecimal.ONE)
                .unit("piece")
                .store(store)
                .lowStockThreshold(5)
                .soldCount(0)
                .build();
    }

    private Customer customer(Store store, String name, String email, String phone) {
        return customerRepository.save(Customer.builder()
                .store(store)
                .name(name)
                .email(email)
                .phone(phone)
                .lifetimeValue(BigDecimal.ZERO)
                .averageOrderValue(BigDecimal.ZERO)
                .totalOrders(0)
                .segment(CustomerSegment.NEW)
                .build());
    }

    private OrderItem item(Product product, int quantity) {
        return OrderItem.builder()
                .product(product)
                .productName(product.getName())
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .costPriceSnapshot(product.getCostPrice())
                .build();
    }

    private List<OrderItem> items(Product a, int qa) {
        return List.of(item(a, qa));
    }

    private List<OrderItem> items(Product a, int qa, Product b, int qb) {
        return List.of(item(a, qa), item(b, qb));
    }

    private Order order(Store store, Customer customer, OrderStatus status, PaymentStatus paymentStatus,
                        List<OrderItem> orderItems, String address, String phone) {
        BigDecimal subtotal = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = Order.builder()
                .orderNumber("ORD-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .store(store)
                .customer(customer)
                .status(status)
                .paymentStatus(paymentStatus)
                .subtotal(subtotal)
                .taxAmount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(subtotal)
                .deliveryAddress(address)
                .deliveryPhone(phone)
                .channel(OrderChannel.STOREFRONT)
                .build();
        order.getItems().addAll(orderItems);
        orderItems.forEach(i -> i.setOrder(order));
        order.setCreatedAt(LocalDateTime.now().minusDays(orderItems.size()));
        return order;
    }
}
