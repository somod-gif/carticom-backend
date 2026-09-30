package com.carticom.service;

import com.carticom.dto.customer.CreateCustomerRequest;
import com.carticom.dto.customer.CustomerResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Customer;
import com.carticom.model.Order;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final PlanGuard planGuard;
    private final OrderRepository orderRepository;

    public CustomerResponse createCustomer(String sellerEmail, CreateCustomerRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        planGuard.requireCustomerCapacity(sellerEmail);

        if (customerRepository.existsByStoreIdAndEmail(store.getId(), request.getEmail())) {
            throw new BadRequestException("Customer with this email already exists");
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .tags(request.getTags())
                .totalOrders(0)
                .store(store)
                .build();

        customerRepository.save(customer);
        log.info("Customer created: {} for store {}", customer.getName(), store.getName());

        return mapToResponse(customer);
    }

    public List<CustomerResponse> getCustomers(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return customerRepository.findByStoreId(store.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CustomerResponse> getCustomersForStore(String sellerEmail, Long storeId) {
        boolean allowed = storeAccessService.resolveStores(sellerEmail).stream()
                .anyMatch(s -> s.getId().equals(storeId));
        if (!allowed) {
            throw new ResourceNotFoundException("Store not found");
        }
        return customerRepository.findByStoreId(storeId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CustomerResponse getCustomerById(String sellerEmail, Long customerId) {
        Store store = getStoreBySeller(sellerEmail);
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return mapToResponse(customer);
    }

    public CustomerResponse updateCustomer(String sellerEmail, Long customerId, CreateCustomerRequest request) {
        Store store = getStoreBySeller(sellerEmail);
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setTags(request.getTags());

        customerRepository.save(customer);
        return mapToResponse(customer);
    }

    public void deleteCustomer(String sellerEmail, Long customerId) {
        Store store = getStoreBySeller(sellerEmail);
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> c.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        customerRepository.delete(customer);
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private CustomerResponse mapToResponse(Customer customer) {
        List<Order> orders = customer.getId() != null
                ? orderRepository.findByCustomerId(customer.getId())
                : List.of();

        String name = customer.getName() != null ? customer.getName().trim() : "";
        String[] parts = name.split("\\s+", 2);
        String firstName = parts.length > 0 ? parts[0] : "";
        String lastName = parts.length > 1 ? parts[1] : "";

        BigDecimal totalSpent = orders.stream()
                .filter(o -> o.getPaymentStatus() != null && "PAID".equals(o.getPaymentStatus().name()))
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime lastOrderDate = orders.stream()
                .map(Order::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        int totalOrders = !orders.isEmpty()
                ? orders.size()
                : (customer.getTotalOrders() != null ? customer.getTotalOrders() : 0);

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(name)
                .firstName(firstName)
                .lastName(lastName)
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .status("ACTIVE")
                .avatarUrl(null)
                .lastOrderDate(lastOrderDate)
                .tags(customer.getTags())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
