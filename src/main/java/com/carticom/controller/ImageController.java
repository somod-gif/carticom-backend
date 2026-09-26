package com.carticom.controller;

import com.carticom.dto.image.ImageUploadResponse;
import com.carticom.service.ByteshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Image upload via Byteship")
public class ImageController {

    private final ByteshipService byteshipService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image", description = "Uploads an image to Byteship CDN and returns the public URL")
    @ApiResponse(responseCode = "200", description = "Image uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Invalid file or upload failed")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file) {
        ImageUploadResponse response = byteshipService.uploadImage(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/file/{filename:.+}")
    @Operation(summary = "Serve locally stored image", description = "Serves an image stored locally when Byteship is not configured")
    public ResponseEntity<org.springframework.core.io.Resource> getLocalFile(@PathVariable String filename) {
        try {
            String safeName = java.nio.file.Paths.get(filename).getFileName().toString()
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            java.nio.file.Path path = java.nio.file.Paths.get(
                            System.getenv().getOrDefault("LOCAL_UPLOAD_DIR", "uploads"))
                    .resolve(safeName).normalize();
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource body = new org.springframework.core.io.FileSystemResource(path);
            String contentType = java.nio.file.Files.probeContentType(path);
            return ResponseEntity.ok()
                    .contentType(contentType != null
                            ? org.springframework.http.MediaType.parseMediaType(contentType)
                            : MediaType.APPLICATION_OCTET_STREAM)
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
