package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.DuplicateDtos.DuplicateProposalResponse;
import com.civicollect.dto.NeedDtos.DuplicateCandidate;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.DuplicateProposalRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.UserRepository;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.service.duplicate.JaccardZoneSimilarityStrategy;
import com.civicollect.service.duplicate.SimilarityStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Détection et gestion des doublons (RF14, RG6).
 * S'appuie sur le pattern Strategy ({@link SimilarityStrategy}) pour calculer la similarité.
 */
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final NeedRepository needRepository;
    private final DuplicateProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final List<SimilarityStrategy> strategies;
    private final SystemSettingService settingService;
    private final HistoryService history;
    private final AuditService audit;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUser;

    /** Similarité de Jaccard entre deux textes, dans [0, 1]. */
    public static double jaccard(String a, String b) {
        return JaccardZoneSimilarityStrategy.jaccard(a, b);
    }

    public static double similarity(Need a, Need b) {
        return new JaccardZoneSimilarityStrategy().compute(a, b);
    }

    private double computeSimilarity(Need a, Need b) {
        if (strategies == null || strategies.isEmpty()) {
            return similarity(a, b);
        }
        return strategies.stream()
                .mapToDouble(s -> s.compute(a, b))
                .average()
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findCandidates(Need need) {
        if (need.getCategory() == null) return List.of();
        Set<NeedStatus> statuses = EnumSet.of(NeedStatus.RECU, NeedStatus.EN_ANALYSE, NeedStatus.RETENU,
                NeedStatus.PLANIFIE, NeedStatus.EN_COURS);
        double threshold = settingService.getDuplicatesThreshold();
        return needRepository
                .findTop200ByCategoryIdAndIdNotAndMergedIntoIsNullAndStatusInOrderBySubmittedAtDesc(
                        need.getCategory().getId(), need.getId(), statuses)
                .stream()
                .map(o -> new DuplicateCandidate(o.getId(), o.getTrackingNumber(), o.getTitle(), o.getStatus(),
                        round(computeSimilarity(need, o))))
                .filter(c -> c.similarity() >= threshold)
                .sorted(Comparator.comparingDouble(DuplicateCandidate::similarity).reversed())
                .limit(10)
                .toList();
    }

    /** Recherche et synchronise les propositions de doublons pour une contribution donnée. */
    @Transactional
    public List<DuplicateProposalResponse> findProposalsForContribution(Long needId) {
        Need need = needRepository.findById(needId)
                .orElseThrow(() -> new NotFoundException("Contribution introuvable"));
        if (need.getCategory() != null && need.getMergedInto() == null) {
            Set<NeedStatus> statuses = EnumSet.of(NeedStatus.RECU, NeedStatus.EN_ANALYSE, NeedStatus.RETENU,
                    NeedStatus.PLANIFIE, NeedStatus.EN_COURS);
            double threshold = settingService.getDuplicatesThreshold();
            List<Need> others = needRepository
                    .findTop200ByCategoryIdAndIdNotAndMergedIntoIsNullAndStatusInOrderBySubmittedAtDesc(
                            need.getCategory().getId(), need.getId(), statuses);
            for (Need other : others) {
                double sim = round(computeSimilarity(need, other));
                if (sim >= threshold) {
                    ensureProposalExists(need, other, sim);
                }
            }
        }
        return proposalRepository.findByNeedId(needId).stream()
                .map(DuplicateDetectionService::toResponse)
                .toList();
    }

    /** Analyse les contributions actives et retourne les propositions de doublons potentiels. */
    @Transactional
    public List<DuplicateProposalResponse> listPotentialProposals() {
        Set<NeedStatus> statuses = EnumSet.of(NeedStatus.RECU, NeedStatus.EN_ANALYSE, NeedStatus.RETENU,
                NeedStatus.PLANIFIE, NeedStatus.EN_COURS);
        List<Need> active = needRepository.findByStatusInAndMergedIntoIsNullOrderBySubmittedAtDesc(statuses);
        double threshold = settingService.getDuplicatesThreshold();

        for (int i = 0; i < active.size(); i++) {
            Need a = active.get(i);
            if (a.getCategory() == null) continue;
            for (int j = i + 1; j < active.size(); j++) {
                Need b = active.get(j);
                if (b.getCategory() == null || !a.getCategory().getId().equals(b.getCategory().getId())) continue;
                double sim = round(computeSimilarity(a, b));
                if (sim >= threshold) {
                    ensureProposalExists(a, b, sim);
                }
            }
        }

        return proposalRepository.findByStatusOrderBySimilarityDesc(DuplicateStatus.POTENTIAL).stream()
                .filter(p -> p.getSourceNeed().getMergedInto() == null && p.getTargetNeed().getMergedInto() == null)
                .map(DuplicateDetectionService::toResponse)
                .toList();
    }

    /** Confirme un doublon et rattache la contribution secondaire à la principale si applicable. */
    @Transactional
    public DuplicateProposalResponse confirmProposal(Long proposalId) {
        DuplicateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Proposition de doublon introuvable"));
        if (proposal.getStatus() == DuplicateStatus.CONFIRMED) {
            return toResponse(proposal);
        }
        User reviewer = userRepository.getReferenceById(currentUser.require().getId());
        proposal.setStatus(DuplicateStatus.CONFIRMED);
        proposal.setReviewedBy(reviewer);
        proposal.setReviewedAt(Instant.now());

        Need primary = proposal.getSourceNeed();
        Need duplicate = proposal.getTargetNeed();
        if (primary.getId() > duplicate.getId()) {
            Need tmp = primary;
            primary = duplicate;
            duplicate = tmp;
        }

        if (duplicate.getMergedInto() == null && primary.getMergedInto() == null) {
            if (primary.getStatus() == NeedStatus.RECU) {
                primary.setStatus(NeedStatus.EN_ANALYSE);
                history.record(primary, "VALIDATED_BY_MERGE", NeedStatus.RECU, NeedStatus.EN_ANALYSE,
                        "Doublon confirmé avec " + duplicate.getTrackingNumber());
            }
            duplicate.setMergedInto(primary);
            history.record(duplicate, "DUPLICATE_CONFIRMED", duplicate.getStatus(), duplicate.getStatus(),
                    "Confirmé comme doublon de " + primary.getTrackingNumber());
            notificationService.notifyRequester(duplicate,
                    "Votre contribution a été rattachée à une demande similaire",
                    "Votre contribution « " + duplicate.getTitle() + " » a été identifiée comme doublon de "
                            + primary.getTrackingNumber() + ".");
        }

        audit.log("DUPLICATE_CONFIRMED", "DuplicateProposal", proposalId,
                "primary=" + primary.getId() + ", duplicate=" + duplicate.getId());
        return toResponse(proposal);
    }

    @Transactional
    public DuplicateProposalResponse ignoreProposal(Long proposalId) {
        DuplicateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Proposition de doublon introuvable"));
        if (proposal.getStatus() == DuplicateStatus.CONFIRMED) {
            throw new BusinessRuleException("Un doublon déjà confirmé ne peut plus être ignoré");
        }
        proposal.setStatus(DuplicateStatus.IGNORED);
        proposal.setReviewedBy(userRepository.getReferenceById(currentUser.require().getId()));
        proposal.setReviewedAt(Instant.now());
        audit.log("DUPLICATE_IGNORED", "DuplicateProposal", proposalId, null);
        return toResponse(proposal);
    }

    private void ensureProposalExists(Need a, Need b, double similarity) {
        Need first = a.getId() < b.getId() ? a : b;
        Need second = a.getId() < b.getId() ? b : a;
        Optional<DuplicateProposal> existing = proposalRepository.findBetween(first.getId(), second.getId());
        if (existing.isEmpty()) {
            DuplicateProposal p = new DuplicateProposal();
            p.setSourceNeed(first);
            p.setTargetNeed(second);
            p.setSimilarity(similarity);
            p.setStatus(DuplicateStatus.POTENTIAL);
            proposalRepository.save(p);
        } else {
            DuplicateProposal p = existing.get();
            if (p.getStatus() == DuplicateStatus.POTENTIAL) {
                p.setSimilarity(similarity);
            }
        }
    }

    private static DuplicateProposalResponse toResponse(DuplicateProposal p) {
        return new DuplicateProposalResponse(
                p.getId(),
                p.getSourceNeed().getId(),
                p.getSourceNeed().getTrackingNumber(),
                p.getSourceNeed().getTitle(),
                p.getSourceNeed().getStatus(),
                p.getTargetNeed().getId(),
                p.getTargetNeed().getTrackingNumber(),
                p.getTargetNeed().getTitle(),
                p.getTargetNeed().getStatus(),
                p.getSimilarity(),
                p.getStatus(),
                p.getReviewedBy() != null ? p.getReviewedBy().getFullName() : null,
                p.getReviewedAt(),
                p.getCreatedAt());
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
