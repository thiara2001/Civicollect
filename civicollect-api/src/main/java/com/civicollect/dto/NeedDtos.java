package com.civicollect.dto;

import com.civicollect.domain.NeedStatus;
import com.civicollect.domain.RequesterType;
import com.civicollect.domain.Urgency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class NeedDtos {
    private NeedDtos() {}

    public record NamedRef(Long id, String name) {}

    /** Brouillon ou soumission d'un habitant / d'une association (RF6, RF8). */
    public record NeedRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            Long categoryId,
            Long zoneId,
            @Size(max = 255) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            Urgency urgency) {}

    /** Soumission anonyme / semi-anonyme (RF4). Le champ « website » est un honeypot anti-robot. */
    public record AnonymousNeedRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull Long categoryId,
            Long zoneId,
            @Size(max = 255) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            Urgency urgency,
            @Email String contactEmail,
            @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Numéro de téléphone invalide") String contactPhone,
            String website) {}

    /** Saisie par un agent pour une personne non connectée (RF9). */
    public record AgentEntryRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull Long categoryId,
            Long zoneId,
            @Size(max = 255) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            Urgency urgency,
            @NotBlank @Size(max = 120) String requesterName,
            @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Numéro de téléphone invalide") String requesterPhone,
            @NotNull RequesterType requesterType) {}

    public record NeedResponse(
            Long id, String trackingNumber, String title, String description,
            NamedRef category, NamedRef zone, String address, Double latitude, Double longitude,
            Urgency urgency, NeedStatus status, RequesterType requesterType, boolean anonymous,
            NamedRef assignedService, BigDecimal estimatedCost, Integer estimatedBeneficiaries,
            Integer estimatedDelayDays, Double priorityScore, double priorityAdjustment,
            boolean consolidated, Long mergedIntoId, boolean complementRequested, String rejectionReason,
            long supportCount, boolean supportedByMe, Instant submittedAt, Instant createdAt) {}

    /** Vue publique (transparence, RF30) : aucune donnée personnelle. */
    public record PublicNeedResponse(
            Long id, String trackingNumber, String title, String description,
            String category, String zone, NeedStatus status, String statusLabel,
            long supportCount, Double latitude, Double longitude, Instant submittedAt) {}

    public record TrackingResponse(String trackingNumber, String title, NeedStatus status, String statusLabel,
                                   String rejectionReason, boolean complementRequested,
                                   Instant submittedAt, Instant lastUpdate) {}

    public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record ComplementRequest(@NotBlank @Size(max = 1000) String message) {}

    public record AssignRequest(Long categoryId, Long serviceId) {}

    public record EstimateRequest(@DecimalMin("0.0") BigDecimal cost, @Min(0) Integer beneficiaries, @Min(0) Integer delayDays) {}

    public record StatusChangeRequest(@NotNull NeedStatus target, @Size(max = 1000) String comment) {}

    public record MergeRequest(@NotNull @Size(min = 2, message = "Au moins deux besoins sont nécessaires") List<Long> needIds,
                               @NotBlank @Size(max = 200) String title,
                               @Size(max = 4000) String description) {}

    public record CommentRequest(@NotBlank @Size(max = 1000) String content, boolean internal) {}

    public record CommentResponse(Long id, String author, String content, boolean internal, Instant createdAt) {}

    public record HistoryEntry(Long id, String actor, String action, NeedStatus fromStatus, NeedStatus toStatus,
                               String details, Instant createdAt) {}

    public record DuplicateCandidate(Long id, String trackingNumber, String title, NeedStatus status, double similarity) {}

    public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes, Instant createdAt) {}

    public record SupportResponse(Long needId, long supportCount, boolean supportedByMe) {}

    public record MapPoint(Long id, String title, double latitude, double longitude, String category,
                           NeedStatus status, Urgency urgency, Double priorityScore) {}

    public record NeedFilter(
            String q, Long categoryId, Long zoneId, NeedStatus status, Urgency urgency, RequesterType requesterType,
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {}
}
