package com.carticom.service;

import com.carticom.dto.auth.AuthResponse;
import com.carticom.dto.staff.AcceptInviteRequest;
import com.carticom.dto.staff.StaffInviteResponse;
import com.carticom.dto.staff.StaffMemberResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ForbiddenException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Role;
import com.carticom.model.StaffInvite;
import com.carticom.model.Store;
import com.carticom.model.StoreMember;
import com.carticom.model.User;
import com.carticom.repository.StaffInviteRepository;
import com.carticom.repository.StoreMemberRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import com.carticom.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffInviteService {

    private final StaffInviteRepository staffInviteRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SendByteService sendByteService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public StaffInviteResponse createInvite(String vendorEmail, Long storeId, String inviteEmail) {
        User vendor = storeAccessService.resolveUser(vendorEmail);
        if (vendor.getRole() != Role.VENDOR && vendor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Vendor access required");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        if (!store.getSeller().getId().equals(vendor.getId())) {
            throw new ForbiddenException("You can only invite staff to your own store");
        }

        if (userRepository.existsByEmail(inviteEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        staffInviteRepository.findByStoreIdAndEmailAndStatus(storeId, inviteEmail, StaffInvite.Status.PENDING)
                .ifPresent(staffInviteRepository::delete);

        StaffInvite invite = StaffInvite.builder()
                .store(store)
                .email(inviteEmail)
                .token(UUID.randomUUID().toString().replace("-", ""))
                .status(StaffInvite.Status.PENDING)
                .invitedBy(vendor)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        staffInviteRepository.save(invite);

        String inviteUrl = baseUrl + "/invite/" + invite.getToken();
        sendInviteEmail(inviteEmail, store.getName(), vendor.getFullName(), inviteUrl);

        log.info("Staff invite created for {} to store {}", inviteEmail, store.getName());

        return StaffInviteResponse.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .status(invite.getStatus().name())
                .inviteUrl(inviteUrl)
                .expiresAt(invite.getExpiresAt())
                .storeName(store.getName())
                .build();
    }

    public StaffInviteResponse getInvitePreview(String token) {
        StaffInvite invite = requireValidInvite(token);
        return StaffInviteResponse.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .status(invite.getStatus().name())
                .expiresAt(invite.getExpiresAt())
                .storeName(invite.getStore().getName())
                .build();
    }

    @Transactional
    public AuthResponse acceptInvite(String token, AcceptInviteRequest request) {
        StaffInvite invite = requireValidInvite(token);

        User user = userRepository.findByEmail(invite.getEmail()).orElse(null);
        if (user == null) {
            user = User.builder()
                    .fullName(request.getFullName())
                    .email(invite.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.STAFF)
                    .build();
            userRepository.save(user);
        } else if (user.getRole() != Role.STAFF) {
            throw new BadRequestException("This email is linked to a different account");
        }

        if (!storeMemberRepository.existsByStoreIdAndUserId(invite.getStore().getId(), user.getId())) {
            storeMemberRepository.save(StoreMember.builder()
                    .store(invite.getStore())
                    .user(user)
                    .build());
        }

        invite.setStatus(StaffInvite.Status.ACCEPTED);
        staffInviteRepository.save(invite);

        log.info("Staff invite accepted: {} joined store {}", invite.getEmail(), invite.getStore().getName());

        return AuthResponse.builder()
                .token(jwtTokenProvider.generateToken(user.getEmail()))
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    public List<StaffMemberResponse> listStaff(String vendorEmail, Long storeId) {
        Store store = requireOwnedStore(vendorEmail, storeId);
        return storeMemberRepository.findByStoreId(store.getId()).stream()
                .map(m -> StaffMemberResponse.builder()
                        .userId(m.getUser().getId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .role(m.getUser().getRole().name())
                        .build())
                .toList();
    }

    public List<StaffInviteResponse> listInvites(String vendorEmail, Long storeId) {
        Store store = requireOwnedStore(vendorEmail, storeId);
        return staffInviteRepository.findByStoreIdOrderByCreatedAtDesc(store.getId()).stream()
                .map(i -> StaffInviteResponse.builder()
                        .id(i.getId())
                        .email(i.getEmail())
                        .status(i.getStatus().name())
                        .expiresAt(i.getExpiresAt())
                        .storeName(store.getName())
                        .build())
                .toList();
    }

    @Transactional
    public void removeStaff(String vendorEmail, Long storeId, Long userId) {
        Store store = requireOwnedStore(vendorEmail, storeId);
        if (userId.equals(store.getSeller().getId())) {
            throw new BadRequestException("The store owner cannot be removed");
        }
        storeMemberRepository.deleteByStoreIdAndUserId(store.getId(), userId);
        log.info("Staff removed: user {} from store {}", userId, store.getName());
    }

    private Store requireOwnedStore(String vendorEmail, Long storeId) {
        User vendor = storeAccessService.resolveUser(vendorEmail);
        if (vendor.getRole() != Role.VENDOR && vendor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Vendor access required");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        if (!store.getSeller().getId().equals(vendor.getId())) {
            throw new ForbiddenException("You can only manage your own store");
        }
        return store;
    }

    private StaffInvite requireValidInvite(String token) {
        StaffInvite invite = staffInviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));
        if (invite.getStatus() != StaffInvite.Status.PENDING) {
            throw new BadRequestException("This invite has already been used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(StaffInvite.Status.EXPIRED);
            staffInviteRepository.save(invite);
            throw new BadRequestException("This invite has expired");
        }
        return invite;
    }

    private void sendInviteEmail(String to, String storeName, String inviterName, String inviteUrl) {
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;border:1px solid #e2e8f0;border-radius:12px">
                  <h2 style="color:#2563eb;margin-top:0">You're invited to run %s</h2>
                  <p><strong>%s</strong> invited you to join <strong>%s</strong> on Carticom as store staff.</p>
                  <p style="color:#64748b;font-size:13px">This invite expires in 7 days.</p>
                  <p><a href="%s" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block">Accept invitation</a></p>
                  <p style="color:#94a3b8;font-size:12px">If the button doesn't work, copy this link:<br>%s</p>
                </div>
                """.formatted(storeName, inviterName, storeName, inviteUrl, inviteUrl);
        sendByteService.send(to, "Join " + storeName + " on Carticom", html);
    }
}
