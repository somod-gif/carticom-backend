package com.carticom.service;

import com.carticom.dto.customer.CreateCustomerRequest;
import com.carticom.dto.customer.CustomerResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Customer;
import com.carticom.model.Store;
import com.carticom.model.User;
import com.carticom.repository.CustomerRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .totalOrders(customer.getTotalOrders())
                .tags(customer.getTags())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
