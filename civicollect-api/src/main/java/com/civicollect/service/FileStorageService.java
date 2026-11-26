package com.civicollect.service;

import com.civicollect.config.AppProperties;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.NotFoundException;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Stockage local des pièces jointes (photo, document) : type, taille et signature vérifiés (RF6). */
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "application/pdf", ".pdf");

    private final Path root;
    private final long maxSize;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().uploadDir()).toAbsolutePath().normalize();
        this.maxSize = props.storage().maxFileSizeBytes();
    }

    public record StoredFile(String relativePath, String originalName, String contentType, long size) {}

    public StoredFile store(MultipartFile file, Long needId) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("Fichier vide");
        if (file.getSize() > maxSize) throw new BusinessRuleException("Fichier trop volumineux");
        String contentType = file.getContentType();
        String ext = contentType == null ? null : ALLOWED.get(contentType);
        if (ext == null) throw new BusinessRuleException("Type de fichier non autorisé (JPEG, PNG ou PDF)");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (!matchesSignature(contentType, head)) {
                throw new BusinessRuleException("Le contenu du fichier ne correspond pas à son type");
            }
            Path dir = root.resolve(String.valueOf(needId)).normalize();
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + ext;       // nom généré : jamais celui du client
            Path target = dir.resolve(stored).normalize();
            try (InputStream full = file.getInputStream()) {
                Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(needId + "/" + stored, sanitizeName(file.getOriginalFilename()), contentType, file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Resource load(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root) || !Files.isReadable(p)) {
            throw new NotFoundException("Fichier introuvable");
        }
        return new PathResource(p);
    }

    private static boolean matchesSignature(String type, byte[] h) {
        if (h.length < 4) return false;
        return switch (type) {
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "application/pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            default -> false;
        };
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "fichier";
        String base = Paths.get(name.replace('\\', '/')).getFileName().toString();
        base = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
