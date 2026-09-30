package com.carticom.dto.staff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMemberResponse {
    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private Boolean active;
    private java.time.LocalDateTime invitedAt;
}
