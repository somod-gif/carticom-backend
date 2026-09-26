package com.carticom.controller;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.dto.staff.AcceptInviteRequest;
import com.carticom.dto.staff.StaffInviteRequest;
import com.carticom.dto.staff.StaffInviteResponse;
import com.carticom.dto.staff.StaffMemberResponse;
import com.carticom.service.StaffInviteService;
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
@Tag(name = "Staff", description = "Store staff invitations and membership")
public class StaffController {

    private final StaffInviteService staffInviteService;

    @PostMapping("/stores/{storeId}/staff/invites")
    @Operation(summary = "Invite a staff member", description = "Vendor invites a staff member by email and receives an invite URL")
    public ResponseEntity<StaffInviteResponse> createInvite(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody StaffInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffInviteService.createInvite(authentication.getName(), storeId, request.getEmail()));
    }

    @GetMapping("/stores/{storeId}/staff")
    @Operation(summary = "List staff members", description = "Vendor lists all staff members of their store")
    public ResponseEntity<List<StaffMemberResponse>> listStaff(
            Authentication authentication,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(staffInviteService.listStaff(authentication.getName(), storeId));
    }

    @GetMapping("/stores/{storeId}/staff/invites")
    @Operation(summary = "List staff invites", description = "Vendor lists invitations sent for their store")
    public ResponseEntity<List<StaffInviteResponse>> listInvites(
            Authentication authentication,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(staffInviteService.listInvites(authentication.getName(), storeId));
    }

    @DeleteMapping("/stores/{storeId}/staff/{userId}")
    @Operation(summary = "Remove a staff member", description = "Vendor removes a staff member from their store")
    public ResponseEntity<Void> removeStaff(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long userId) {
        staffInviteService.removeStaff(authentication.getName(), storeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invites/{token}")
    @Operation(summary = "Preview invite", description = "Public endpoint to preview a staff invitation")
    public ResponseEntity<StaffInviteResponse> getInvite(@PathVariable String token) {
        return ResponseEntity.ok(staffInviteService.getInvitePreview(token));
    }

    @PostMapping("/invites/{token}/accept")
    @Operation(summary = "Accept invite", description = "Public endpoint: creates the staff account and returns a JWT")
    public ResponseEntity<AuthResponse> acceptInvite(
            @PathVariable String token,
            @Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(staffInviteService.acceptInvite(token, request));
    }
}
