package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.model.WaitlistEntry;
import com.carticom.repository.WaitlistRepository;
import com.carticom.service.SendByteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Early-access waitlist")
public class WaitlistController {

    private final WaitlistRepository waitlistRepository;
    private final SendByteService sendByteService;

    public record WaitlistJoinRequest(String name, String email, String businessName, String phone) {
    }

    @PostMapping("/join")
    @Operation(summary = "Join the waitlist", description = "Idempotent: joining twice is accepted")
    public ResponseEntity<SuccessResponse<Map<String, String>>> join(@RequestBody WaitlistJoinRequest request) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        if (email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(new SuccessResponse<>(false, Map.of("message", "A valid email is required.")));
        }
        if (!waitlistRepository.existsByEmail(email)) {
            WaitlistEntry entry = waitlistRepository.save(WaitlistEntry.builder()
                    .name(request.name() == null || request.name().isBlank() ? "Early Access Member" : request.name().trim())
                    .email(email)
                    .businessName(blankToNull(request.businessName()))
                    .phone(blankToNull(request.phone()))
                    .build());
            sendConfirmationEmail(entry);
        }
        return ResponseEntity.ok(new SuccessResponse<>(true,
                Map.of("message", "You're on the list! We'll be in touch.")));
    }

    @GetMapping("/check")
    @Operation(summary = "Check your waitlist spot", description = "Returns the member's status and waiting position")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> check(@RequestParam String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        Map<String, Object> body = new LinkedHashMap<>();
        if (normalized.isBlank() || !normalized.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(new SuccessResponse<>(false, Map.of("message", "A valid email is required.")));
        }
        WaitlistEntry entry = waitlistRepository.findByEmail(normalized).orElse(null);
        if (entry == null) {
            body.put("status", "NOT_FOUND");
            return ResponseEntity.ok(new SuccessResponse<>(true, body));
        }
        String status = entry.getStatus() == null || entry.getStatus().isBlank() ? "WAITING" : entry.getStatus().toUpperCase();
        body.put("status", status);
        if ("WAITING".equals(status) && entry.getCreatedAt() != null) {
            long ahead = waitlistRepository.countByStatusAndCreatedAtBefore("WAITING", entry.getCreatedAt());
            body.put("position", ahead + 1);
        }
        return ResponseEntity.ok(new SuccessResponse<>(true, body));
    }

    private void sendConfirmationEmail(WaitlistEntry entry) {
        try {
            String firstName = entry.getName() == null ? "" : entry.getName().split(" ")[0];
            String html = """
                    <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                      <h2 style="color:#2563eb;margin-top:0">You're on the Carticom early-access list</h2>
                      <p>Hi %s,</p>
                      <p>Thanks for joining! We'll email you the moment your spot opens up so you can set up your shop.</p>
                      <p style="color:#64748b;font-size:13px">You can check your place in line any time on your waitlist page.</p>
                      <p style="margin-bottom:0"><a href="https://carticom.cv/waitlist" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block">Check my spot</a></p>
                    </div>
                    """.formatted(firstName);
            sendByteService.send(entry.getEmail(), "You're on the Carticom waitlist", html);
        } catch (Exception e) {
            // Email failure must never break joining the waitlist
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
