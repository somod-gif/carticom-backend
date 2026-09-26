package com.carticom.service;

import com.carticom.exception.ForbiddenException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Role;
import com.carticom.model.Store;
import com.carticom.model.StoreMember;
import com.carticom.model.User;
import com.carticom.repository.StoreMemberRepository;
import com.carticom.repository.StoreRepository;
import com.carticom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreAccessService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;

    public User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public Store resolveStore(String email) {
        User user = resolveUser(email);
        return switch (user.getRole()) {
            case VENDOR -> storeRepository.findBySellerId(user.getId())
                    .stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No store found"));
            case STAFF -> storeMemberRepository.findFirstByUserId(user.getId())
                    .map(StoreMember::getStore)
                    .orElseThrow(() -> new ResourceNotFoundException("No store found"));
            case ADMIN -> throw new ForbiddenException("Admin accounts cannot access store data directly");
            case CUSTOMER -> throw new ForbiddenException("Customer accounts cannot access store data");
        };
    }

    public List<Store> resolveStores(String email) {
        User user = resolveUser(email);
        return switch (user.getRole()) {
            case VENDOR -> storeRepository.findBySellerId(user.getId());
            case STAFF -> storeMemberRepository.findFirstByUserId(user.getId())
                    .map(m -> List.of(m.getStore()))
                    .orElseGet(List::of);
            case ADMIN, CUSTOMER -> throw new ForbiddenException("Account cannot access store data");
        };
    }

    public void requireVendor(String email) {
        User user = resolveUser(email);
        if (user.getRole() != Role.VENDOR && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Vendor access required");
        }
    }

    public void requireVendorOrStaff(String email) {
        resolveStore(email);
    }
}
