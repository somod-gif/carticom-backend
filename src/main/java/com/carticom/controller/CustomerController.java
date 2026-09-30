package com.carticom.controller;

import com.carticom.dto.customer.CreateCustomerRequest;
import com.carticom.dto.customer.CustomerResponse;
import com.carticom.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management endpoints")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Create a new customer")
    public ResponseEntity<CustomerResponse> createCustomer(
            Authentication authentication,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all customers")
    public ResponseEntity<List<CustomerResponse>> getCustomers(Authentication authentication) {
        return ResponseEntity.ok(customerService.getCustomers(authentication.getName()));
    }

    @GetMapping("/store/{storeId}")
    @Operation(summary = "Get customers for a store")
    public ResponseEntity<List<CustomerResponse>> getStoreCustomers(
            Authentication authentication,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(customerService.getCustomersForStore(authentication.getName(), storeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<CustomerResponse> getCustomer(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(authentication.getName(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer")
    public ResponseEntity<CustomerResponse> updateCustomer(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer")
    public ResponseEntity<Void> deleteCustomer(
            Authentication authentication,
            @PathVariable Long id) {
        customerService.deleteCustomer(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
