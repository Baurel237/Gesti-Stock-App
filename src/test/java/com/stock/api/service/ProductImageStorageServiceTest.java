package com.stock.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductImageStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesPngWithGeneratedFilename() throws Exception {
        ProductImageStorageService storage = new ProductImageStorageService(temporaryDirectory.toString());
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "unsafe.png", "image/png", pngSignature);

        ProductImageStorageService.StoredImage stored = storage.store(file);

        assertEquals("image/png", stored.contentType());
        assertFalse(stored.filename().contains("unsafe"));
        assertTrue(Files.isRegularFile(storage.resolveStoredPath(stored.filename())));
        storage.delete(stored.filename());
        assertFalse(Files.exists(storage.resolveStoredPath(stored.filename())));
    }

    @Test
    void storesJpegRegardlessOfSuppliedMimeType() {
        ProductImageStorageService storage = new ProductImageStorageService(temporaryDirectory.toString());
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "text/plain", jpegHeader);

        ProductImageStorageService.StoredImage stored = storage.store(file);

        assertEquals("image/jpeg", stored.contentType());
        assertTrue(stored.filename().endsWith(".jpg"));
    }

    @Test
    void rejectsUnsupportedContentAndOversizedImage() {
        ProductImageStorageService storage = new ProductImageStorageService(temporaryDirectory.toString());
        MockMultipartFile text = new MockMultipartFile("file", "image.png", "image/png", "not an image".getBytes());
        assertThrows(InvalidProductImageException.class, () -> storage.store(text));

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(InvalidProductImageException.class, () -> storage.store(oversized));
    }

    @Test
    void rejectsPathsContainingDirectorySegments() {
        ProductImageStorageService storage = new ProductImageStorageService(temporaryDirectory.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.resolveStoredPath("../outside.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolveStoredPath("..\\outside.png"));
    }
}
