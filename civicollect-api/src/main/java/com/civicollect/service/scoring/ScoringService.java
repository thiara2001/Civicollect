package com.civicollect.service.scoring;

import com.civicollect.config.AppProperties;
import com.civicollect.domain.*;
import com.civicollect.dto.PriorityDtos.CriterionScore;
import com.civicollect.dto.PriorityDtos.RankedNeed;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.repository.CriterionRepository;
import com.civicollect.repository.EvaluationRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.SupportRepository;
import com.civicollect.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Score de priorité multicritère (RF23-RF25).
 *
 * <pre>
 *   scoreBase  = 100 × Σ(poids_i × score_i) / Σ(poids_i)         avec score_i ∈ [0, 1]
 *   scoreFinal = bornage[0, 100](scoreBase + ajustement manuel)
 * </pre>
 * Le calcul est déterministe : à données identiques, le classement est identique (tri par score décroissant,
 * puis ancienneté, puis identifiant) → reproductible et explicable grâce au détail par critère.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final CriterionRepository criterionRepository;
    private final NeedRepository needRepository;
    private final SupportRepository supportRepository;
    private final EvaluationRepository evaluationRepository;
    private final ZoneRepository zoneRepository;
    private final List<ScoringStrategy> strategies;
    private final AppProperties props;

    /** Classement courant avec les poids enregistrés. */
    @Transactional(readOnly = true)
    public List<RankedNeed> ranking() {
        return compute(null).stream().map(Computed::toDto).toList();
    }

    /** RF25 : simulation de scénario (poids alternatifs), sans rien enregistrer. */
    @Transactional(readOnly = true)
    public List<RankedNeed> simulate(Map<String, Double> weightOverrides) {
        return compute(weightOverrides).stream().map(Computed::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RankedNeed> explain(Long needId) {
        return compute(null).stream().filter(c -> c.need.getId().equals(needId)).findFirst().map(Computed::toDto);
    }

    /** Persiste les scores dans les besoins (cache lu par les listes, cartes et exports). */
    @Transactional
    public int recalculate() {
        List<Computed> all = compute(null);
        all.forEach(c -> c.need.setPriorityScore(c.finalScore));
        return all.size();
    }

    // ---------------------------------------------------------------

    record Computed(int rank, Need need, double base, double finalScore, List<CriterionScore> breakdown) {
        RankedNeed toDto() {
            return new RankedNeed(rank, need.getId(), need.getTrackingNumber(), need.getTitle(),
                    need.getCategory() != null ? need.getCategory().getName() : null,
                    need.getZone() != null ? need.getZone().getName() : null,
                    need.getStatus(), round(base), round(need.getPriorityAdjustment()), round(finalScore), breakdown);
        }
    }

    private List<Computed> compute(Map<String, Double> overrides) {
        List<Criterion> criteria = criterionRepository.findByActiveTrueOrderByIdAsc();
        Map<String, Double> weights = resolveWeights(criteria, overrides);
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        List<Need> ranked = needRepository.findByStatusInAndMergedIntoIsNull(NeedStatus.rankable());
        ScoringContext ctx = buildContext(ranked);

        List<Computed> result = new ArrayList<>();
        for (Need need : ranked) {
            List<CriterionScore> breakdown = new ArrayList<>();
            double weighted = 0.0;
            for (Criterion c : criteria) {
                double w = weights.get(c.getCode());
                double s = strategyFor(c).score(need, c, ctx);
                double contribution = totalWeight > 0 ? 100.0 * w * s / totalWeight : 0.0;
                weighted += contribution;
                breakdown.add(new CriterionScore(c.getCode(), c.getLabel(), w, round(s), round(contribution)));
            }
            double base = weighted;
            double fin = Math.max(0.0, Math.min(100.0, base + need.getPriorityAdjustment()));
            result.add(new Computed(0, need, base, fin, breakdown));
        }
        Comparator<Computed> order = Comparator.comparingDouble((Computed c) -> c.finalScore).reversed()
                .thenComparing((Computed c) -> c.need.getSubmittedAt(), Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing((Computed c) -> c.need.getId());
        result.sort(order);
        List<Computed> ordered = new ArrayList<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            Computed c = result.get(i);
            ordered.add(new Computed(i + 1, c.need, c.base, c.finalScore, c.breakdown));
        }
        return ordered;
    }

    private Map<String, Double> resolveWeights(List<Criterion> criteria, Map<String, Double> overrides) {
        Set<String> known = criteria.stream().map(Criterion::getCode).collect(Collectors.toSet());
        if (overrides != null) {
            for (Map.Entry<String, Double> e : overrides.entrySet()) {
                if (!known.contains(e.getKey())) {
                    throw new BusinessRuleException("Critère inconnu ou inactif : " + e.getKey());
                }
                if (e.getValue() == null || e.getValue() < 0) {
                    throw new BusinessRuleException("Le poids du critère " + e.getKey() + " doit être positif ou nul");
                }
            }
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Criterion c : criteria) {
            weights.put(c.getCode(), overrides != null && overrides.containsKey(c.getCode())
                    ? overrides.get(c.getCode()) : c.getWeight());
        }
        return weights;
    }

    private ScoringStrategy strategyFor(Criterion c) {
        return strategies.stream().filter(s -> s.supports(c)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Aucune stratégie de calcul pour le critère " + c.getCode()));
    }

    private ScoringContext buildContext(List<Need> ranked) {
        List<Long> ids = ranked.stream().map(Need::getId).toList();

        // Demandes = 1 (demandeur) + soutiens ; pour un besoin consolidé on ajoute celles des besoins d'origine.
        Map<Long, Long> ownSupports = supportCounts(ids);
        List<Need> children = ranked.isEmpty() ? List.of() : needRepository.findByMergedIntoIn(ranked);
        Map<Long, Long> childSupports = supportCounts(children.stream().map(Need::getId).toList());
        Map<Long, Integer> requests = new HashMap<>();
        for (Need n : ranked) {
            requests.put(n.getId(), 1 + ownSupports.getOrDefault(n.getId(), 0L).intValue());
        }
        for (Need child : children) {
            requests.merge(child.getMergedInto().getId(),
                    1 + childSupports.getOrDefault(child.getId(), 0L).intValue(), Integer::sum);
        }

        Map<Long, Map<String, Double>> manual = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Evaluation e : evaluationRepository.findByNeedIdIn(ids)) {
                manual.computeIfAbsent(e.getNeed().getId(), k -> new HashMap<>())
                        .put(e.getCriterion().getCode(), e.getScoreValue());
            }
        }

        double maxB = ranked.stream().map(Need::getEstimatedBeneficiaries).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0);
        double maxCost = ranked.stream().map(Need::getEstimatedCost).filter(Objects::nonNull)
                .mapToDouble(java.math.BigDecimal::doubleValue).max().orElse(0);

        Map<Long, Integer> population = new HashMap<>();
        zoneRepository.findAll().forEach(z -> population.put(z.getId(), z.getPopulation() == null ? 0 : z.getPopulation()));
        Map<Long, Double> zoneRates = new HashMap<>();
        for (Object[] row : needRepository.countPerZoneId()) {
            Long zoneId = (Long) row[0];
            long count = (Long) row[1];
            int pop = population.getOrDefault(zoneId, 0);
            zoneRates.put(zoneId, pop > 0 ? count * 1000.0 / pop : (double) count);
        }
        double maxRate = zoneRates.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);

        return new ScoringContext(requests, manual, maxB, maxCost, zoneRates, maxRate, props.scoring().supportCap());
    }

    private Map<Long, Long> supportCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return supportRepository.countByNeedIds(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
