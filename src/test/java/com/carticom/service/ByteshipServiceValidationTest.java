package com.carticom.service;

import com.carticom.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ByteshipService#validateImage(MultipartFile)} —
 * magic-byte sniffing, extension whitelist and size cap.
 */
class ByteshipServiceValidationTest {

    private ByteshipService service;

    @BeforeEach
    void setUp() {
        // Blank apiKey → local-storage path; validation runs before that branch.
        service = new ByteshipService("", "https://api.byteship.dev");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    private static final byte[] GIF_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
            0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00
    };

    private static final byte[] WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20
    };

    private static final byte[] BMP_BYTES = {
            0x42, 0x4D, 0x36, 0x00, 0x03, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x36, 0x00, 0x00, 0x00, 0x28, 0x00
    };

    private static final byte[] TEXT_BYTES = {
            0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x2C, 0x20, 0x77,
            0x6F, 0x72, 0x6C, 0x64, 0x21, 0x0A
    };

    private static MultipartFile file(byte[] content, String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    // ── Valid images pass ────────────────────────────────────────────

    @Test
    void validPngPasses() {
        MultipartFile f = file(PNG_BYTES, "photo.png", "image/png");
        assertDoesNotThrow(() -> service.validateImage(f));
    }

    @Test
    void validJpegPasses() {
        MultipartFile f = file(JPEG_BYTES, "photo.jpg", "image/jpeg");
        assertDoesNotThrow(() -> service.validateImage(f));
    }

    @Test
    void validGifPasses() {
        MultipartFile f = file(GIF_BYTES, "anim.gif", "image/gif");
        assertDoesNotThrow(() -> service.validateImage(f));
    }

    @Test
    void validWebpPasses() {
        MultipartFile f = file(WEBP_BYTES, "img.webp", "image/webp");
        assertDoesNotThrow(() -> service.validateImage(f));
    }

    @Test
    void validBmpPasses() {
        MultipartFile f = file(BMP_BYTES, "img.bmp", "image/bmp");
        assertDoesNotThrow(() -> service.validateImage(f));
    }

    // ── Magic-byte mismatch ──────────────────────────────────────────

    @Test
    void textFileWithImageContentTypeIsRejected() {
        MultipartFile f = file(TEXT_BYTES, "evil.png", "image/png");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(f));
        assertEquals("File must be a valid image", ex.getMessage());
    }

    @Test
    void randomBytesWithImageExtensionAreRejected() {
        byte[] garbage = new byte[16];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = (byte) (i * 37 + 11);
        }
        MultipartFile f = file(garbage, "fake.jpg", "image/jpeg");
        assertThrows(BadRequestException.class, () -> service.validateImage(f));
    }

    @Test
    void tooShortToBeAnImageIsRejected() {
        byte[] tiny = {0x01, 0x02, 0x03};
        MultipartFile f = file(tiny, "tiny.png", "image/png");
        assertThrows(BadRequestException.class, () -> service.validateImage(f));
    }

    // ── Extension whitelist ──────────────────────────────────────────

    @Test
    void disallowedExtensionIsRejected() {
        MultipartFile f = file(PNG_BYTES, "malware.exe", "image/png");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(f));
        assertTrue(ex.getMessage().contains("extension not allowed"));
    }

    @Test
    void svgExtensionIsRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        MultipartFile f = file(svg, "icon.svg", "image/svg+xml");
        assertThrows(BadRequestException.class, () -> service.validateImage(f));
    }

    // ── Size cap ─────────────────────────────────────────────────────

    @Test
    void oversizeFileIsRejected() throws IOException {
        byte[] big = new byte[10 * 1024 * 1024 + 1]; // 10MB + 1 byte
        // Put PNG magic bytes at the start so only the size check fires.
        big[0] = (byte) 0x89;
        big[1] = 0x50;
        big[2] = 0x4E;
        big[3] = 0x47;
        MultipartFile f = file(big, "huge.png", "image/png");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(f));
        assertEquals("File is too large. Maximum size is 10MB.", ex.getMessage());
    }

    // ── Empty file ───────────────────────────────────────────────────

    @Test
    void emptyFileIsRejected() {
        MultipartFile f = file(new byte[0], "empty.png", "image/png");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(f));
        assertEquals("File is empty", ex.getMessage());
    }

    // ── Content-type guard ───────────────────────────────────────────

    @Test
    void nonImageContentTypeIsRejected() {
        MultipartFile f = file(PNG_BYTES, "photo.png", "text/plain");
        assertThrows(BadRequestException.class, () -> service.validateImage(f));
    }

    @Test
    void nullContentTypeIsRejected() {
        MultipartFile f = file(PNG_BYTES, "photo.png", null);
        assertThrows(BadRequestException.class, () -> service.validateImage(f));
    }
}
