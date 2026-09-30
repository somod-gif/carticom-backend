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
import java.util.UUID;

@Slf4j
@Service
public class ByteshipService {

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
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File must be an image");
        }

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
