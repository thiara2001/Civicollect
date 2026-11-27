package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.AdminDtos.CriterionRequest;
import com.civicollect.dto.NeedDtos.NeedResponse;
import com.civicollect.dto.PriorityDtos.*;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ConflictException;
import com.civicollect.exception.ForbiddenOperationException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.*;
import com.civicollect.security.AuthenticatedUser;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.service.scoring.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Paramétrage des critères, évaluations manuelles, ajustements motivés et rapport de priorisation. */
@Service
@RequiredArgsConstructor
public class PriorityService {

    private final CriterionRepository criteria;
    private final EvaluationRepository evaluations;
    private final DecisionRepository decisions;
    private final NeedRepository needs;
    private final UserRepository users;
    private final ScoringService scoring;
    private final NeedService needService;
    private final HistoryService history;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ExportService exportService;

    // ------------------------ critères (RF24, RG4) ------------------------

    @Transactional(readOnly = true)
    public List<CriterionResponse> listCriteria() {
        return criteria.findAllByOrderByIdAsc().stream().map(PriorityService::toResponse).toList();
    }

    /** RG4 : seuls un administrateur ou un élu habilité modifient les poids. */
    @Transactional
    public CriterionResponse updateWeight(Long criterionId, double weight) {
        requirePriorityManager("Seul un administrateur ou un décideur habilité peut modifier les pondérations");
        Criterion c = criteria.findById(criterionId).orElseThrow(() -> new NotFoundException("Critère introuvable"));
        double old = c.getWeight();
        c.setWeight(weight);
        audit.log("CRITERION_WEIGHT_CHANGED", "Criterion", c.getId(), c.getCode() + " : " + old + " → " + weight);
        scoring.recalculate();
        return toResponse(c);
    }

    /** RG4 : mise à jour groupée des pondérations de critères (par liste ou par map code -> poids). */
    @Transactional
    public List<CriterionResponse> updateWeightsBatch(BatchWeightUpdateRequest r) {
        requirePriorityManager("Seul un administrateur ou un décideur habilité peut modifier les pondérations");
        boolean hasList = r.criteria() != null && !r.criteria().isEmpty();
        boolean hasMap = r.weights() != null && !r.weights().isEmpty();
        if (!hasList && !hasMap) {
            throw new BusinessRuleException("Au moins une pondération doit être fournie");
        }
        if (hasList) {
            for (CriterionWeightUpdate item : r.criteria()) {
                Criterion c = item.id() != null
                        ? criteria.findById(item.id()).orElseThrow(() -> new NotFoundException("Critère introuvable"))
                        : criteria.findByCode(item.code()).orElseThrow(() -> new NotFoundException("Critère introuvable : " + item.code()));
                double old = c.getWeight();
                c.setWeight(item.weight());
                audit.log("CRITERION_WEIGHT_CHANGED", "Criterion", c.getId(), c.getCode() + " : " + old + " → " + item.weight());
            }
        }
        if (hasMap) {
            for (Map.Entry<String, Double> e : r.weights().entrySet()) {
                if (e.getValue() == null || e.getValue() < 0) {
                    throw new BusinessRuleException("Pondération invalide pour " + e.getKey());
                }
                Criterion c = criteria.findByCode(e.getKey())
                        .orElseThrow(() -> new NotFoundException("Critère introuvable : " + e.getKey()));
                double old = c.getWeight();
                c.setWeight(e.getValue());
                audit.log("CRITERION_WEIGHT_CHANGED", "Criterion", c.getId(), c.getCode() + " : " + old + " → " + e.getValue());
            }
        }
        scoring.recalculate();
        return listCriteria();
    }

    @Transactional
    public CriterionResponse createCriterion(CriterionRequest r) {
        requirePriorityManager("Seul un administrateur peut créer un critère");
        if (criteria.findByCode(r.code()).isPresent()) throw new ConflictException("Ce code de critère existe déjà");
        Criterion c = new Criterion();
        c.setCode(r.code());
        c.setLabel(r.label());
        c.setWeight(r.weight());
        c.setType(r.type());
        c.setActive(r.active());
        criteria.save(c);
        audit.log("CRITERION_CREATED", "Criterion", c.getId(), c.getCode());
        return toResponse(c);
    }

    @Transactional
    public CriterionResponse updateCriterion(Long id, CriterionRequest r) {
        requirePriorityManager("Seul un administrateur peut modifier un critère");
        Criterion c = criteria.findById(id).orElseThrow(() -> new NotFoundException("Critère introuvable"));
        c.setLabel(r.label());
        c.setWeight(r.weight());
        c.setActive(r.active());          // le code et le type d'un critère existant ne changent pas
        audit.log("CRITERION_UPDATED", "Criterion", id, c.getCode());
        scoring.recalculate();
        return toResponse(c);
    }

    // ------------------------ évaluations manuelles ------------------------

    @Transactional
    public List<CriterionScore> evaluate(Long needId, EvaluationRequest r) {
        AuthenticatedUser au = currentUser.require();
        Need need = needs.findById(needId).orElseThrow(() -> new NotFoundException("Besoin introuvable"));
        if (!need.getStatus().isValidated()) {
            throw new BusinessRuleException("Un besoin ne peut être évalué qu'après validation par un agent");
        }
        for (EvaluationItem item : r.evaluations()) {
            Criterion c = criteria.findByCode(item.criterionCode())
                    .filter(x -> x.getType() == CriterionType.MANUAL)
                    .orElseThrow(() -> new BusinessRuleException("Critère manuel inconnu : " + item.criterionCode()));
            Evaluation e = evaluations.findByNeedIdAndCriterionId(needId, c.getId()).orElseGet(() -> {
                Evaluation n = new Evaluation();
                n.setNeed(need);
                n.setCriterion(c);
                return n;
            });
            e.setScoreValue(item.value());
            e.setEvaluatedBy(users.getReferenceById(au.getId()));
            evaluations.save(e);
        }
        history.record(need, "EVALUATED", need.getStatus(), need.getStatus(),
                r.evaluations().stream().map(i -> i.criterionCode() + "=" + i.value()).collect(Collectors.joining(", ")));
        scoring.recalculate();
        return scoring.explain(needId).map(RankedNeed::breakdown).orElse(List.of());
    }

    // ------------------------ ajustement manuel (RF26, RG5) ------------------------

    @Transactional
    public RankedNeed adjust(Long needId, AdjustmentRequest r) {
        requirePriorityManager("Seul un administrateur ou un décideur habilité peut ajuster le classement");
        Need need = needs.findById(needId).orElseThrow(() -> new NotFoundException("Besoin introuvable"));
        if (!need.getStatus().isRankable()) {
            throw new BusinessRuleException("Seul un besoin validé et en cours de traitement peut être ajusté (RG2)");
        }
        if (r.reason() == null || r.reason().isBlank()) {
            throw new BusinessRuleException("Tout ajustement manuel doit être justifié");
        }
        Decision d = new Decision();
        d.setNeed(need);
        d.setDecider(users.getReferenceById(currentUser.require().getId()));
        d.setPreviousAdjustment(need.getPriorityAdjustment());
        d.setNewAdjustment(r.adjustment());
        d.setReason(r.reason().trim());
        decisions.save(d);
        need.setPriorityAdjustment(r.adjustment());
        history.record(need, "PRIORITY_ADJUSTED", need.getStatus(), need.getStatus(),
                d.getPreviousAdjustment() + " → " + d.getNewAdjustment() + " : " + d.getReason());
        audit.log("PRIORITY_ADJUSTED", "Need", needId, d.getPreviousAdjustment() + " → " + d.getNewAdjustment());
        scoring.recalculate();
        return scoring.explain(needId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DecisionResponse> decisions(Long needId) {
        return decisions.findByNeedIdOrderByCreatedAtDesc(needId).stream()
                .map(d -> new DecisionResponse(d.getId(), d.getDecider().getFullName(), d.getPreviousAdjustment(),
                        d.getNewAdjustment(), d.getReason(), d.getCreatedAt()))
                .toList();
    }

    // ------------------------ rapport (RF27) ------------------------

    public record Report(byte[] content, String contentType, String filename) {}

    @Transactional(readOnly = true)
    public Report report(String format, Integer top) {
        List<RankedNeed> ranking = scoring.ranking();
        if (top != null && top > 0 && top < ranking.size()) ranking = ranking.subList(0, top);
        List<CriterionResponse> activeCriteria = listCriteria().stream().filter(CriterionResponse::active).toList();

        List<String> headers = new ArrayList<>(List.of("Rang", "N° suivi", "Titre", "Catégorie", "Zone", "Statut",
                "Score base", "Ajust.", "Score final"));
        activeCriteria.forEach(c -> headers.add(c.code()));
        List<List<String>> rows = new ArrayList<>();
        for (RankedNeed r : ranking) {
            List<String> row = new ArrayList<>(List.of(String.valueOf(r.rank()), nz(r.trackingNumber()), r.title(),
                    nz(r.category()), nz(r.zone()), r.status().getLabel(), String.valueOf(r.baseScore()),
                    String.valueOf(r.adjustment()), String.valueOf(r.finalScore())));
            Map<String, Double> contrib = r.breakdown().stream()
                    .collect(Collectors.toMap(CriterionScore::code, CriterionScore::contribution));
            activeCriteria.forEach(c -> row.add(String.valueOf(contrib.getOrDefault(c.code(), 0.0))));
            rows.add(row);
        }
        String date = LocalDate.now().toString();
        if ("pdf".equalsIgnoreCase(format)) {
            List<String> notes = new ArrayList<>();
            notes.add("Généré le " + date + " — " + ranking.size() + " besoin(s) classé(s)");
            notes.add("Pondérations : " + activeCriteria.stream()
                    .map(c -> c.code() + "=" + c.weight()).collect(Collectors.joining(", ")));
            notes.add("Chaque colonne de critère indique la contribution en points au score de base.");
            return new Report(exportService.pdf("CiviCollect — Rapport de priorisation", notes, headers, rows),
                    "application/pdf", "rapport-priorisation-" + date + ".pdf");
        }
        if ("csv".equalsIgnoreCase(format)) {
            return new Report(exportService.csv(headers, rows), "text/csv; charset=UTF-8",
                    "rapport-priorisation-" + date + ".csv");
        }
        throw new BusinessRuleException("Format non supporté (pdf ou csv)");
    }

    // ---------------------------------------------------------------

    private void requirePriorityManager(String message) {
        if (!currentUser.require().canManagePriorities()) throw new ForbiddenOperationException(message);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static CriterionResponse toResponse(Criterion c) {
        return new CriterionResponse(c.getId(), c.getCode(), c.getLabel(), c.getWeight(), c.getType(), c.isActive());
    }
}
