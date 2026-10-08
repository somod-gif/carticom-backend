package com.carticom.controller;

import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.WaitlistEntry;
import com.carticom.repository.WaitlistRepository;
import com.carticom.service.SendByteService;
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
    private final SendByteService sendByteService;

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
        String newStatus = status.toUpperCase();
        String previousStatus = entry.getStatus();
        entry.setStatus(newStatus);
        waitlistRepository.save(entry);
        notifyMember(entry, previousStatus, newStatus);
        return ResponseEntity.ok(toResponse(entry));
    }

    private void notifyMember(WaitlistEntry entry, String previousStatus, String newStatus) {
        if (newStatus.equals(previousStatus)) {
            return;
        }
        try {
            switch (newStatus) {
                case "INVITED" -> sendByteService.send(entry.getEmail(),
                        "You're invited to try Carticom",
                        memberEmail(entry,
                                "You're invited!",
                                "Your spot on the Carticom early-access list has opened up. Create your account to start setting up your shop.",
                                "Create my account",
                                "https://carticom.cv/register"));
                case "APPROVED" -> sendByteService.send(entry.getEmail(),
                        "Your Carticom early access is approved",
                        memberEmail(entry,
                                "Your early access is approved",
                                "Your account is ready. Sign in to start building your shop whenever you like.",
                                "Open Carticom",
                                "https://carticom.cv/login"));
                default -> { /* WAITING / REJECTED: no email */ }
            }
        } catch (Exception e) {
            // Email failure must never break the status update
        }
    }

    private String memberEmail(WaitlistEntry entry, String heading, String body,
                               String buttonLabel, String buttonUrl) {
        String firstName = entry.getName() == null || entry.getName().isBlank()
                ? "there" : entry.getName().split(" ")[0];
        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                  <h2 style="color:#2563eb;margin-top:0">%s</h2>
                  <p>Hi %s,</p>
                  <p>%s</p>
                  <p style="margin-bottom:0"><a href="%s" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block">%s</a></p>
                </div>
                """.formatted(heading, firstName, body, buttonUrl, buttonLabel);
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
