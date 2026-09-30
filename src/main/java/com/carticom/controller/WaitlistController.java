package com.carticom.controller;

import com.carticom.dto.common.SuccessResponse;
import com.carticom.model.WaitlistEntry;
import com.carticom.repository.WaitlistRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Early-access waitlist")
public class WaitlistController {

    private final WaitlistRepository waitlistRepository;

    public record WaitlistJoinRequest(String name, String email) {
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
            waitlistRepository.save(WaitlistEntry.builder()
                    .name(request.name() == null ? "Early Access Member" : request.name().trim())
                    .email(email)
                    .build());
        }
        return ResponseEntity.ok(new SuccessResponse<>(true,
                Map.of("message", "You're on the list! We'll be in touch.")));
    }
}
