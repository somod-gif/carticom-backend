package com.carticom.controller;

import com.carticom.dto.address.AddressRequest;
import com.carticom.dto.address.AddressResponse;
import com.carticom.dto.common.SuccessResponse;
import com.carticom.exception.ForbiddenException;
import com.carticom.service.AddressService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "Saved addresses for the signed-in customer")
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/customers/addresses")
    @Operation(summary = "List my saved addresses",
            description = "Requires sign in. Lists the signed-in customer's own addresses, default first.")
    public ResponseEntity<SuccessResponse<List<AddressResponse>>> getMyAddresses(Authentication authentication) {
        List<AddressResponse> addresses = addressService.getMyAddresses(requireSignedIn(authentication));
        return ResponseEntity.ok(new SuccessResponse<>(true, addresses));
    }

    @PostMapping("/customers/addresses")
    @Operation(summary = "Save a new address",
            description = "Requires sign in. Street, city and country are needed; phone is optional. "
                    + "A customer can save up to 20 addresses.")
    public ResponseEntity<SuccessResponse<AddressResponse>> createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.createAddress(requireSignedIn(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>(true, response));
    }

    @PutMapping("/customers/addresses/{id}")
    @Operation(summary = "Update one of my saved addresses",
            description = "Requires sign in. Only your own addresses can be updated — "
                    + "anyone else's returns a 404.")
    public ResponseEntity<SuccessResponse<AddressResponse>> updateAddress(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.updateAddress(requireSignedIn(authentication), id, request);
        return ResponseEntity.ok(new SuccessResponse<>(true, response));
    }

    @DeleteMapping("/customers/addresses/{id}")
    @Operation(summary = "Delete one of my saved addresses",
            description = "Requires sign in. Only your own addresses can be deleted. Keep at least one "
                    + "address, and make another address the default before deleting the current one.")
    public ResponseEntity<Void> deleteAddress(Authentication authentication, @PathVariable Long id) {
        addressService.deleteAddress(requireSignedIn(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/customers/addresses/{id}/default")
    @Operation(summary = "Make one address my default",
            description = "Requires sign in. Switches the default over to this address and clears it "
                    + "from the others.")
    public ResponseEntity<SuccessResponse<AddressResponse>> setDefaultAddress(
            Authentication authentication,
            @PathVariable Long id) {
        AddressResponse response = addressService.setDefaultAddress(requireSignedIn(authentication), id);
        return ResponseEntity.ok(new SuccessResponse<>(true, response));
    }

    private String requireSignedIn(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ForbiddenException("Please sign in to manage your addresses");
        }
        return authentication.getName();
    }
}
