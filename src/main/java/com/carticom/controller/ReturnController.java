package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.dto.returnrequest.ReturnCreateRequest;
import com.carticom.dto.returnrequest.ReturnResponse;
import com.carticom.exception.ForbiddenException;
import com.carticom.service.ReturnService;
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
@Tag(name = "Returns", description = "Customer return and refund requests for delivered orders")
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping("/orders/{id}/returns")
    @Operation(summary = "Request a return for an order",
            description = "Requires sign in. Only delivered orders can be returned, and only one request "
                    + "at a time per order.")
    public ResponseEntity<SuccessResponse<ReturnResponse>> createReturn(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ReturnCreateRequest request) {
        ReturnResponse response = returnService.createReturn(requireSignedIn(authentication), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>(true, response));
    }

    @GetMapping("/orders/{id}/returns")
    @Operation(summary = "Get the return requests for an order",
            description = "Requires sign in. Only the customer who placed the order can see its returns.")
    public ResponseEntity<SuccessResponse<List<ReturnResponse>>> getReturnsForOrder(
            Authentication authentication,
            @PathVariable Long id) {
        List<ReturnResponse> returns = returnService.getReturnsForOrder(requireSignedIn(authentication), id);
        return ResponseEntity.ok(new SuccessResponse<>(true, returns));
    }

    @GetMapping("/returns/mine")
    @Operation(summary = "Get my return requests",
            description = "Requires sign in. Lists the signed-in customer's return requests, newest first.")
    public ResponseEntity<SuccessResponse<List<ReturnResponse>>> getMyReturns(Authentication authentication) {
        List<ReturnResponse> returns = returnService.getMyReturns(requireSignedIn(authentication));
        return ResponseEntity.ok(new SuccessResponse<>(true, returns));
    }

    private String requireSignedIn(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ForbiddenException("Please sign in to manage your returns");
        }
        return authentication.getName();
    }
}
