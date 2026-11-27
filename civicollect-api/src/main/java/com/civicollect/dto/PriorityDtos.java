package com.civicollect.dto;

import com.civicollect.domain.CriterionType;
import com.civicollect.domain.NeedStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PriorityDtos {
    private PriorityDtos() {}

    /** Détail d'un critère dans le score : permet d'expliquer le classement (critère de réussite). */
    public record CriterionScore(String code, String label, double weight, double normalizedScore, double contribution) {}

    public record RankedNeed(int rank, Long needId, String trackingNumber, String title, String category, String zone,
                             NeedStatus status, double baseScore, double adjustment, double finalScore,
                             List<CriterionScore> breakdown) {}

    public record SimulationRequest(@NotEmpty Map<String, Double> weights, Integer top) {}

    public record CriterionResponse(Long id, String code, String label, double weight, CriterionType type, boolean active) {}

    public record WeightUpdateRequest(@DecimalMin("0.0") @DecimalMax("100.0") double weight) {}

    public record AdjustmentRequest(@DecimalMin("-30.0") @DecimalMax("30.0") double adjustment,
                                    @NotBlank @Size(max = 1000) String reason) {}

    public record DecisionResponse(Long id, String decider, double previousAdjustment, double newAdjustment,
                                   String reason, Instant createdAt) {}

    public record EvaluationItem(@NotBlank String criterionCode,
                                 @DecimalMin("0.0") @DecimalMax("10.0") double value) {}

    public record EvaluationRequest(@NotEmpty List<@Valid EvaluationItem> evaluations) {}

    public record CriterionWeightUpdate(
            Long id, String code,
            @DecimalMin("0.0") @DecimalMax("100.0") double weight) {}

    public record BatchWeightUpdateRequest(
            List<@Valid CriterionWeightUpdate> criteria,
            Map<String, @DecimalMin("0.0") @DecimalMax("100.0") Double> weights) {}
}
