package com.arknow.storage.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local file upload/download handler for dev environment.
 * Handles PUT (upload) and GET (download) for /uploads/** paths.
 * In production, these requests go directly to OSS via presigned URLs.
 */
@RestController
@RequestMapping("/uploads")
public class LocalUploadController {
    private static final Logger log = LoggerFactory.getLogger(LocalUploadController.class);
    private static final Path UPLOAD_ROOT = Paths.get(System.getProperty("user.dir"), "uploads");

    @PutMapping("/**")
    public ResponseEntity<Void> handlePut(HttpServletRequest request) throws IOException {
        String path = extractPath(request);
        Path target = UPLOAD_ROOT.resolve(path).normalize();
        if (!target.startsWith(UPLOAD_ROOT)) return ResponseEntity.badRequest().build();

        Files.createDirectories(target.getParent());
        Files.write(target, request.getInputStream().readAllBytes());
        log.info("Local upload saved: {}", target);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> handleGet(HttpServletRequest request) {
        String path = extractPath(request);
        Path target = UPLOAD_ROOT.resolve(path).normalize();
        if (!target.startsWith(UPLOAD_ROOT) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(target);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + target.getFileName() + "\"")
                .body(resource);
    }

    private String extractPath(HttpServletRequest request) {
        return request.getRequestURI().replaceFirst("^/uploads/", "");
    }
}
