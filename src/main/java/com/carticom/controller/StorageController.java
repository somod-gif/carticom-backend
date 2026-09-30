package com.carticom.controller;

import com.carticom.dto.image.ImageUploadResponse;
import com.carticom.service.ByteshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final ByteshipService byteshipService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(byteshipService.uploadImage(file));
    }
}
