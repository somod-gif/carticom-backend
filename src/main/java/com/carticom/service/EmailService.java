package com.carticom.service;

import com.carticom.dto.email.CreateEmailCampaignRequest;
import com.carticom.dto.email.EmailCampaignResponse;
import com.carticom.dto.email.SendEmailRequest;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.*;
import com.carticom.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailCampaignRepository campaignRepository;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    public void sendTransactionalEmail(SendEmailRequest request) {
        try {
            WebClient client = WebClient.builder()
                    .baseUrl("https://api.resend.com")
                    .defaultHeader("Authorization", "Bearer " + resendApiKey)
                    .build();

            Map<String, Object> body = Map.of(
                    "from", fromEmail,
                    "to", List.of(request.getTo()),
                    "subject", request.getSubject(),
                    "html", request.getBody()
            );

            client.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Email sent to {}", request.getTo());
        } catch (WebClientResponseException e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }

    public EmailCampaignResponse createCampaign(String sellerEmail, CreateEmailCampaignRequest request) {
        Store store = getStoreBySeller(sellerEmail);

        EmailCampaign campaign = EmailCampaign.builder()
                .name(request.getName())
                .subject(request.getSubject())
                .body(request.getBody())
                .store(store)
                .status(CampaignStatus.DRAFT)
                .totalRecipients(0)
                .totalSent(0)
                .totalOpened(0)
                .totalClicked(0)
                .build();

        campaignRepository.save(campaign);
        return mapToResponse(campaign);
    }

    public List<EmailCampaignResponse> getCampaigns(String sellerEmail) {
        Store store = getStoreBySeller(sellerEmail);
        return campaignRepository.findByStoreIdOrderByCreatedAtDesc(store.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public EmailCampaignResponse sendCampaign(String sellerEmail, Long campaignId) {
        Store store = getStoreBySeller(sellerEmail);
        EmailCampaign campaign = campaignRepository.findById(campaignId)
                .filter(c -> c.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));

        List<Customer> customers = customerRepository.findByStoreId(store.getId());
        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setTotalRecipients(customers.size());
        campaignRepository.save(campaign);

        int sent = 0;
        for (Customer customer : customers) {
            try {
                SendEmailRequest emailRequest = new SendEmailRequest(
                        customer.getEmail(),
                        campaign.getSubject(),
                        campaign.getBody()
                );
                sendTransactionalEmail(emailRequest);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", customer.getEmail(), e.getMessage());
            }
        }

        campaign.setStatus(CampaignStatus.SENT);
        campaign.setTotalSent(sent);
        campaignRepository.save(campaign);

        return mapToResponse(campaign);
    }

    private Store getStoreBySeller(String sellerEmail) {
        return storeAccessService.resolveStore(sellerEmail);
    }

    private EmailCampaignResponse mapToResponse(EmailCampaign campaign) {
        return EmailCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .subject(campaign.getSubject())
                .body(campaign.getBody())
                .status(campaign.getStatus().name())
                .totalRecipients(campaign.getTotalRecipients())
                .totalSent(campaign.getTotalSent())
                .totalOpened(campaign.getTotalOpened())
                .totalClicked(campaign.getTotalClicked())
                .createdAt(campaign.getCreatedAt())
                .build();
    }
}
