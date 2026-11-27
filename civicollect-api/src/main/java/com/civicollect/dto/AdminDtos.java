package com.civicollect.dto;

import com.civicollect.domain.CriterionType;
import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {}

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email String email,
            @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Numéro de téléphone invalide") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            Long municipalServiceId,
            boolean habilitated) {}

    public record UpdateUserRequest(Role role, Long municipalServiceId, Boolean enabled, Boolean habilitated) {}

    public record UpdateUserFullRequest(
            @Size(max = 120) String fullName,
            @Email String email,
            @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Numéro de téléphone invalide") String phone,
            Role role,
            Long municipalServiceId,
            Boolean enabled,
            Boolean habilitated) {}

    public record UpdateUserStatusRequest(@NotNull Boolean enabled) {}

    public record UpdateUserRoleRequest(@NotNull Role role, Boolean habilitated) {}

    public record AdminDashboardResponse(
            long totalUsers, long activeUsers, Map<String, Long> usersByRole,
            long totalCategories, long activeCategories, long totalQuartiers, long totalServices,
            long totalContributions, long pendingModeration, long potentialDuplicates,
            long totalAuditLogs, List<AuditEntry> recentLogs) {}

    public record SystemSettingsResponse(
            int scoringSupportCap, double duplicatesThreshold, int rateLimitPerHour,
            long maxFileSizeBytes, long jwtExpirationMinutes) {}

    public record UpdateSystemSettingsRequest(
            @Min(1) @Max(1000) Integer scoringSupportCap,
            @DecimalMin("0.05") @DecimalMax("1.0") Double duplicatesThreshold,
            @Min(1) @Max(10000) Integer rateLimitPerHour,
            @Min(1024) @Max(52428800) Long maxFileSizeBytes) {}

    /** Référentiel générique : catégorie (name, description), zone (name, lat, lon, population), service (name). */
    public record ReferenceRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Min(0) Integer population) {}

    public record ZoneResponse(Long id, String name, Double latitude, Double longitude, Integer population) {}

    public record CategoryResponse(Long id, String name, String description, boolean active) {}

    public record CriterionRequest(@NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z_]+$", message = "Code en MAJUSCULES et _") String code,
                                   @NotBlank @Size(max = 150) String label,
                                   @DecimalMin("0.0") @DecimalMax("100.0") double weight,
                                   @NotNull CriterionType type,
                                   boolean active) {}

    public record TemplateRequest(@NotBlank @Size(max = 255) String subject, @NotBlank @Size(max = 2000) String body) {}

    public record TemplateResponse(NeedStatus status, String subject, String body) {}

    public record AuditEntry(Long id, String actor, String action, String entityType, Long entityId,
                             String details, Instant createdAt) {}

    public record NotificationResponse(Long id, String subject, String message, Long needId, boolean read, Instant createdAt) {}
}
