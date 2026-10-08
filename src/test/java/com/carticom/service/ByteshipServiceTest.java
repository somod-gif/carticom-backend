package com.carticom.service;

import com.carticom.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Upload validation contract of {@link ByteshipService}: magic-byte
 * sniffing, the extension whitelist, the explicit size cap and the
 * content-type check.
 */
class ByteshipServiceTest {

    private ByteshipService service;

    @BeforeEach
    void setUp() {
        // Blank API key => local-disk fallback path; validation runs
        // before either storage backend is chosen.
        service = new ByteshipService("", "http://localhost:8080");
    }

    private static MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    private static byte[] pngBytes() {
        // PNG signature (89 50 4E 47 0D 0A 1A 0A) + IHDR header filler.
        byte[] png = new byte[32];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        png[4] = 0x0D;
        png[5] = 0x0A;
        png[6] = 0x1A;
        png[7] = 0x0A;
        return png;
    }

    @Test
    void validPngPasses() {
        assertDoesNotThrow(() ->
                service.validateImage(file("logo.png", "image/png", pngBytes())));
    }

    @Test
    void validJpegPasses() {
        byte[] jpeg = new byte[32];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE0;

        assertDoesNotThrow(() ->
                service.validateImage(file("photo.jpg", "image/jpeg", jpeg)));
    }

    @Test
    void validGifPasses() {
        byte[] gif = new byte[32];
        gif[0] = 0x47; // G
        gif[1] = 0x49; // I
        gif[2] = 0x46; // F
        gif[3] = 0x38; // 8

        assertDoesNotThrow(() ->
                service.validateImage(file("anim.gif", "image/gif", gif)));
    }

    @Test
    void validWebpPasses() {
        byte[] webp = new byte[32];
        webp[0] = 0x52; // R
        webp[1] = 0x49; // I
        webp[2] = 0x46; // F
        webp[3] = 0x46; // F
        webp[8] = 0x57; // W
        webp[9] = 0x45; // E
        webp[10] = 0x42; // B
        webp[11] = 0x50; // P

        assertDoesNotThrow(() ->
                service.validateImage(file("pic.webp", "image/webp", webp)));
    }

    @Test
    void validBmpPasses() {
        byte[] bmp = new byte[32];
        bmp[0] = 0x42; // B
        bmp[1] = 0x4D; // M

        assertDoesNotThrow(() ->
                service.validateImage(file("art.bmp", "image/bmp", bmp)));
    }

    @Test
    void textFileWithImageContentTypeIsRejected() {
        MockMultipartFile spoofed = file("evil.png", "image/png",
                "this is definitely not an image".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(spoofed));
        assertEquals("File must be a valid image", ex.getMessage());
    }

    @Test
    void magicByteMismatchIsRejected() {
        // PDF magic bytes smuggled in behind an image/png content type.
        byte[] pdf = "%PDF-1.4\n%".getBytes();
        MockMultipartFile pdfFile = file("document.png", "image/png", pdf);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(pdfFile));
        assertEquals("File must be a valid image", ex.getMessage());
    }

    @Test
    void oversizeFileIsRejected() {
        byte[] oversize = new byte[10 * 1024 * 1024 + 1];
        oversize[0] = (byte) 0x89;
        oversize[1] = 0x50;
        oversize[2] = 0x4E;
        oversize[3] = 0x47;
        MockMultipartFile big = file("big.png", "image/png", oversize);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(big));
        assertEquals("File is too large. Maximum size is 10MB.", ex.getMessage());
    }

    @Test
    void disallowedExtensionIsRejected() {
        // Valid PNG bytes, but the extension is not whitelisted.
        MockMultipartFile exe = file("malware.exe", "image/png", pngBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(exe));
        assertEquals("File extension not allowed. Allowed: .jpg, .jpeg, .png, .gif, .webp, .bmp",
                ex.getMessage());
    }

    @Test
    void nonImageContentTypeIsRejected() {
        MockMultipartFile text = file("notes.txt", "text/plain",
                "hello".getBytes());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(text));
        assertEquals("File must be an image", ex.getMessage());
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = file("empty.png", "image/png", new byte[0]);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(empty));
        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void truncatedHeaderIsRejected() {
        // Only 4 bytes: a valid PNG signature but too short for the
        // WEBP (RIFF....WEBP) check which needs bytes 8-11.
        byte[] shortHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47};
        MockMultipartFile truncated = file("tiny.png", "image/png", shortHeader);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.validateImage(truncated));
        assertEquals("File must be a valid image", ex.getMessage());
    }
}
