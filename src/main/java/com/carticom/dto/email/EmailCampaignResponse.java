package com.carticom.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailCampaignResponse {
    private Long id;
    private String name;
    private String subject;
    private String body;
    private String status;
    private Integer totalRecipients;
    private Integer totalSent;
    private Integer totalOpened;
    private Integer totalClicked;
    private LocalDateTime createdAt;
}
