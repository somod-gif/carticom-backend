package com.carticom.controller;

import com.carticom.dto.email.CreateEmailCampaignRequest;
import com.carticom.dto.email.EmailCampaignResponse;
import com.carticom.dto.email.SendEmailRequest;
import com.carticom.service.EmailService;
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
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Tag(name = "Email Marketing", description = "Email campaigns via Resend")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    @Operation(summary = "Send transactional email")
    public ResponseEntity<Void> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        emailService.sendTransactionalEmail(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/campaigns")
    @Operation(summary = "Create email campaign")
    public ResponseEntity<EmailCampaignResponse> createCampaign(
            Authentication authentication,
            @Valid @RequestBody CreateEmailCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emailService.createCampaign(authentication.getName(), request));
    }

    @GetMapping("/campaigns")
    @Operation(summary = "Get all campaigns")
    public ResponseEntity<List<EmailCampaignResponse>> getCampaigns(Authentication authentication) {
        return ResponseEntity.ok(emailService.getCampaigns(authentication.getName()));
    }

    @PostMapping("/campaigns/{id}/send")
    @Operation(summary = "Send campaign to all customers")
    public ResponseEntity<EmailCampaignResponse> sendCampaign(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(emailService.sendCampaign(authentication.getName(), id));
    }
}
