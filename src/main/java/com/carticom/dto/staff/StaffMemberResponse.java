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
    private Long storeId;
    private String fullName;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String status;
    private Boolean active;
    private java.time.LocalDateTime invitedAt;
}
