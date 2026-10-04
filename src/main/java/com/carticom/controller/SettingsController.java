package com.carticom.controller;

import com.carticom.dto.store.StoreResponse;
import com.carticom.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<StoreResponse> getSettings(Authentication authentication) {
        return ResponseEntity.ok(storeService.getStoreByUser(authentication.getName()));
    }
}
