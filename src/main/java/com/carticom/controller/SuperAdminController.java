package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.WaitlistEntry;
import com.carticom.repository.WaitlistRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/super-admin/waitlist")
@RequiredArgsConstructor
@Tag(name = "Super Admin Waitlist", description = "Manage early-access merchant waitlist")
public class SuperAdminController {

    private static final Set<String> STATUSES = Set.of("WAITING", "INVITED", "APPROVED", "REJECTED");

    private final WaitlistRepository waitlistRepository;

    public record WaitlistResponse(Long id, String name, String email, String businessName,
                                   String phone, String status, String createdAt) {}

    @GetMapping
    @Operation(summary = "List waitlist entries, optionally filtered by status")
    public ResponseEntity<List<WaitlistResponse>> list(
            @RequestParam(required = false) String status) {
        List<WaitlistEntry> entries = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? waitlistRepository.findAll()
                : waitlistRepository.findByStatus(status.toUpperCase());
        return ResponseEntity.ok(entries.stream().map(this::toResponse).toList());
    }

    @GetMapping("/stats")
    @Operation(summary = "Waitlist counters by status")
    public ResponseEntity<Map<String, Object>> stats() {
        List<WaitlistEntry> all = waitlistRepository.findAll();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("waiting", count(all, "WAITING"));
        res.put("invited", count(all, "INVITED"));
        res.put("approved", count(all, "APPROVED"));
        res.put("rejected", count(all, "REJECTED"));
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update an entry's status")
    public ResponseEntity<WaitlistResponse> updateStatus(@PathVariable Long id,
                                                         @RequestParam String status) {
        if (!STATUSES.contains(status.toUpperCase())) {
            throw new BadRequestException("Unknown status: " + status);
        }
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found"));
        entry.setStatus(status.toUpperCase());
        waitlistRepository.save(entry);
        return ResponseEntity.ok(toResponse(entry));
    }

    private long count(List<WaitlistEntry> entries, String status) {
        return entries.stream()
                .filter(e -> status.equals(normalize(e.getStatus())))
                .count();
    }

    private String normalize(String status) {
        return status == null || status.isBlank() ? "WAITING" : status.toUpperCase();
    }

    private WaitlistResponse toResponse(WaitlistEntry e) {
        return new WaitlistResponse(
                e.getId(),
                e.getName(),
                e.getEmail(),
                e.getBusinessName(),
                e.getPhone(),
                normalize(e.getStatus()),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    }
}
