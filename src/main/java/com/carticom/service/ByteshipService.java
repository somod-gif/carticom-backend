package com.carticom.service;

import com.carticom.dto.image.ImageUploadResponse;
import com.carticom.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ByteshipService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    private final WebClient webClient;
    private final String apiKey;

    public ByteshipService(
            @Value("${byteship.api-key}") String apiKey,
            @Value("${byteship.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public ImageUploadResponse uploadImage(MultipartFile file) {
        validateImage(file);

        String contentType = file.getContentType();

        if (apiKey == null || apiKey.isBlank()) {
            return storeLocally(file, contentType);
        }

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String path = "products/" + filename;

        try {
            byte[] fileBytes = file.getBytes();

            Map<String, Object> createResponse = webClient.put()
                    .uri("/v1/files/{path}", path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "byteSize", fileBytes.length,
                            "contentType", contentType,
                            "method", "single",
                            "visibility", "public"
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (createResponse == null) {
                throw new BadRequestException("Failed to create upload session");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> upload = (Map<String, Object>) createResponse.get("upload");
            String uploadUrl = (String) upload.get("url");

            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) upload.get("headers");

            webClient.put()
                    .uri(uploadUrl)
                    .contentType(MediaType.parseMediaType(contentType))
                    .bodyValue(fileBytes)
                    .headers(h -> {
                        if (headers != null) {
                            headers.forEach((key, value) -> h.add(key, value.toString()));
                        }
                    })
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            webClient.post()
                    .uri("/v1/files/{path}/upload/complete", path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("uploadId", upload.get("id")))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> fileData = (Map<String, Object>) createResponse.get("file");
            String url = (String) fileData.get("url");

            log.info("Image uploaded to Byteship: {}", path);

            return ImageUploadResponse.builder()
                    .url(url)
                    .path(path)
                    .filename(filename)
                    .build();

        } catch (WebClientResponseException e) {
            log.warn("Byteship API error ({}), falling back to local storage: {}",
                    e.getStatusCode(), e.getMessage());
            return storeLocally(file, contentType);
        } catch (RuntimeException e) {
            log.warn("Byteship unreachable ({}), falling back to local storage",
                    e.getMessage());
            return storeLocally(file, contentType);
        } catch (IOException e) {
            log.error("Failed to read file: {}", e.getMessage());
            throw new BadRequestException("Failed to read file");
        }
    }

    /**
     * Validates the uploaded file: size, extension, content-type and magic bytes.
     * Package-private for testing.
     */
    void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File is too large. Maximum size is 10MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File must be an image");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (!allowed) {
                throw new BadRequestException(
                        "File extension not allowed. Allowed: .jpg, .jpeg, .png, .gif, .webp, .bmp");
            }
        }

        // Magic byte sniffing — read the first bytes and verify they match known
        // image signatures. This is the primary defence against content-type spoofing.
        byte[] header;
        try {
            header = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file");
        }

        if (header.length < 12) {
            throw new BadRequestException("File must be a valid image");
        }

        if (!hasValidImageMagicBytes(header)) {
            throw new BadRequestException("File must be a valid image");
        }
    }

    private boolean hasValidImageMagicBytes(byte[] header) {
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF: 47 49 46 38
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return true;
        }
        // WEBP: RIFF....WEBP
        if (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return true;
        }
        // BMP: 42 4D
        if (header[0] == 0x42 && header[1] == 0x4D) {
            return true;
        }
        return false;
    }

    private ImageUploadResponse storeLocally(MultipartFile file, String contentType) {
        try {
            String safeName = UUID.randomUUID() + "_" +
                    Paths.get(file.getOriginalFilename() == null ? "image.png" : file.getOriginalFilename())
                            .getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dir = Paths.get(System.getenv().getOrDefault("LOCAL_UPLOAD_DIR", "uploads"));
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            Files.write(target, file.getBytes());

            String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/images/file/").path(safeName).toUriString();

            log.info("Byteship not configured - image stored locally: {}", safeName);

            return ImageUploadResponse.builder()
                    .url(url)
                    .path("local/" + safeName)
                    .filename(safeName)
                    .build();
        } catch (IOException e) {
            log.error("Failed to store image locally: {}", e.getMessage());
            throw new BadRequestException("Failed to store image");
        }
    }
}
