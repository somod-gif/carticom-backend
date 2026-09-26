package com.carticom.dto.staff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffInviteResponse {
    private Long id;
    private String email;
    private String status;
    private String inviteUrl;
    private LocalDateTime expiresAt;
    private String storeName;
}
