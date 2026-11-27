package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.NeedDtos.*;
import com.civicollect.event.NeedStatusChangedEvent;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ForbiddenOperationException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.CategoryRepository;
import com.civicollect.repository.MunicipalServiceRepository;
import com.civicollect.repository.NeedRepository;
import com.civicollect.repository.UserRepository;
import com.civicollect.security.AuthenticatedUser;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Cas d'usage du personnel municipal : modération, qualification, affectation, fusion, cycle de vie. */
@Service
@RequiredArgsConstructor
public class NeedWorkflowService {

    private final NeedRepository needs;
    private final CategoryRepository categories;
    private final MunicipalServiceRepository municipalServices;
    private final UserRepository users;
    private final NeedService needService;
    private final NotificationService notificationService;
    private final HistoryService history;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    /** RF12 : file de modération (les plus anciennes soumissions d'abord). */
    @Transactional(readOnly = true)
    public Page<NeedResponse> moderationQueue(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), 100)),
                Sort.by(Sort.Direction.ASC, "submittedAt"));
        return needs.findByStatusAndMergedIntoIsNull(NeedStatus.RECU, p).map(needService::toResponse);
    }

    /** Consulter une contribution à examiner en modération. */
    @Transactional(readOnly = true)
    public NeedResponse getForModeration(Long id) {
        Need n = needService.load(id);
        if (n.getStatus() == NeedStatus.BROUILLON) {
            throw new NotFoundException("Contribution introuvable");
        }
        return needService.toResponse(n);
    }

    /** RF13 : validation. */
    @Transactional
    public NeedResponse validate(Long id) {
        return needService.toResponse(transition(id, NeedStatus.EN_ANALYSE, null, "VALIDATED"));
    }

    /** RF13, RG3 : rejet toujours motivé et notifié. */
    @Transactional
    public NeedResponse reject(Long id, String reason) {
        return needService.toResponse(transition(id, NeedStatus.REJETE, reason, "REJECTED"));
    }

    /** RF13 : demande de complément (le demandeur peut alors modifier sa soumission). */
    @Transactional
    public NeedResponse requestComplement(Long id, String message) {
        Need n = needService.load(id);
        if (n.getStatus() != NeedStatus.RECU) {
            throw new BusinessRuleException("Un complément ne peut être demandé que sur un besoin « Reçu »");
        }
        n.setComplementRequested(true);
        history.record(n, "COMPLEMENT_REQUESTED", NeedStatus.RECU, NeedStatus.RECU, message);
        audit.log("NEED_COMPLEMENT_REQUESTED", "Need", id, null);
        events.publishEvent(new NeedStatusChangedEvent(n, NeedStatus.RECU, NeedStatus.RECU, message));
        return needService.toResponse(n);
    }

    /** RF28 : changement de statut générique, contrôlé par la machine à états de {@link NeedStatus}. */
    @Transactional
    public NeedResponse changeStatus(Long id, StatusChangeRequest r) {
        if (r.target() == NeedStatus.BROUILLON || r.target() == NeedStatus.RECU) {
            throw new BusinessRuleException("Statut cible non autorisé");
        }
        return needService.toResponse(transition(id, r.target(), r.comment(), "STATUS_CHANGED"));
    }

    /** RF16 : affectation à un service municipal et à une catégorie. */
    @Transactional
    public NeedResponse assign(Long id, AssignRequest r) {
        Need n = editable(id);
        if (r.categoryId() != null) {
            n.setCategory(categories.findById(r.categoryId()).filter(Category::isActive)
                    .orElseThrow(() -> new NotFoundException("Catégorie introuvable")));
        }
        if (r.serviceId() != null) {
            n.setAssignedService(municipalServices.findById(r.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service municipal introuvable")));
        }
        String detail = "catégorie=" + (n.getCategory() != null ? n.getCategory().getName() : "-")
                + ", service=" + (n.getAssignedService() != null ? n.getAssignedService().getName() : "-");
        history.record(n, "ASSIGNED", n.getStatus(), n.getStatus(), detail);
        audit.log("NEED_ASSIGNED", "Need", id, detail);
        return needService.toResponse(n);
    }

    /** RF17 : estimation du coût, des bénéficiaires et du délai par l'agent. */
    @Transactional
    public NeedResponse estimate(Long id, EstimateRequest r) {
        Need n = editable(id);
        if (r.cost() != null) n.setEstimatedCost(r.cost());
        if (r.beneficiaries() != null) n.setEstimatedBeneficiaries(r.beneficiaries());
        if (r.delayDays() != null) n.setEstimatedDelayDays(r.delayDays());
        history.record(n, "ESTIMATED", n.getStatus(), n.getStatus(),
                "coût=" + n.getEstimatedCost() + ", bénéficiaires=" + n.getEstimatedBeneficiaries()
                        + ", délai(j)=" + n.getEstimatedDelayDays());
        return needService.toResponse(n);
    }

    /**
     * RF15, RG6 : regroupe plusieurs besoins similaires en un besoin consolidé. Les besoins d'origine
     * conservent un lien (mergedInto) vers le consolidé et leurs demandeurs sont notifiés.
     */
    @Transactional
    public NeedResponse merge(MergeRequest r) {
        AuthenticatedUser au = currentUser.require();
        List<Long> ids = r.needIds().stream().distinct().toList();
        if (ids.size() < 2) throw new BusinessRuleException("Au moins deux besoins distincts sont nécessaires");
        List<Need> sources = needs.findAllById(ids);
        if (sources.size() != ids.size()) throw new NotFoundException("Un des besoins est introuvable");
        for (Need s : sources) {
            if (s.getMergedInto() != null || s.isConsolidated()) {
                throw new BusinessRuleException("Le besoin " + s.getTrackingNumber() + " est déjà regroupé");
            }
            if (s.getStatus() != NeedStatus.RECU && s.getStatus() != NeedStatus.EN_ANALYSE) {
                throw new BusinessRuleException("Seuls les besoins « Reçu » ou « En analyse » peuvent être regroupés");
            }
        }
        Need parent = new Need();
        parent.setTitle(r.title().trim());
        parent.setDescription(r.description() != null && !r.description().isBlank() ? r.description().trim()
                : "Besoin consolidé issu de : " + String.join(", ", sources.stream().map(Need::getTrackingNumber).toList()));
        parent.setCategory(sources.get(0).getCategory());
        parent.setZone(sources.stream().map(Need::getZone).filter(Objects::nonNull).findFirst().orElse(null));
        parent.setUrgency(sources.stream().map(Need::getUrgency).max(Comparator.naturalOrder()).orElse(Urgency.MOYENNE));
        parent.setStatus(NeedStatus.EN_ANALYSE);                       // la fusion par un agent vaut validation (RG2)
        parent.setConsolidated(true);
        parent.setCreatedByAgent(users.getReferenceById(au.getId()));
        parent.setSubmittedAt(sources.stream().map(Need::getSubmittedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(Instant.now()));
        sources.stream().filter(s -> s.getLatitude() != null && s.getLongitude() != null).findFirst().ifPresent(s -> {
            parent.setLatitude(s.getLatitude());
            parent.setLongitude(s.getLongitude());
        });
        int beneficiaries = sources.stream().map(Need::getEstimatedBeneficiaries).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (beneficiaries > 0) parent.setEstimatedBeneficiaries(beneficiaries);
        needs.save(parent);
        NeedService.assignTrackingNumber(parent);
        history.record(parent, "CONSOLIDATED", null, NeedStatus.EN_ANALYSE, "Issu de " + ids.size() + " besoins");

        for (Need s : sources) {
            s.setMergedInto(parent);
            history.record(s, "MERGED", s.getStatus(), s.getStatus(), "Regroupé dans " + parent.getTrackingNumber());
            notificationService.notifyRequester(s, "Votre demande a été regroupée",
                    "Votre demande « " + s.getTitle() + " » a été regroupée avec des demandes similaires dans le besoin "
                            + parent.getTrackingNumber() + ". Vous pouvez suivre l'avancement avec le numéro initial.");
        }
        audit.log("NEEDS_MERGED", "Need", parent.getId(), "sources=" + ids);
        return needService.toResponse(parent);
    }

    // ---------------------------------------------------------------

    private Need transition(Long id, NeedStatus target, String reason, String action) {
        AuthenticatedUser au = currentUser.require();
        Need n = needService.load(id);
        if (n.getMergedInto() != null) {
            throw new BusinessRuleException("Ce besoin a été regroupé : agissez sur le besoin consolidé");
        }
        NeedStatus from = n.getStatus();
        if (!from.canMoveTo(target)) {
            throw new BusinessRuleException("Transition interdite : " + from.getLabel() + " → " + target.getLabel());
        }
        if (target == NeedStatus.REJETE && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("Le rejet d'un besoin doit être motivé");
        }
        if (target == NeedStatus.RETENU && au.getRole() != Role.ELU && au.getRole() != Role.ADMIN) {
            throw new ForbiddenOperationException("Seul un élu ou un administrateur peut retenir un besoin");
        }
        n.setStatus(target);
        n.setComplementRequested(false);
        if (target == NeedStatus.REJETE) n.setRejectionReason(reason.trim());
        history.record(n, action, from, target, reason);
        audit.log("NEED_" + target.name(), "Need", id, "depuis " + from.name());
        events.publishEvent(new NeedStatusChangedEvent(n, from, target, reason));
        return n;
    }

    private Need editable(Long id) {
        Need n = needService.load(id);
        if (n.getStatus() == NeedStatus.BROUILLON || n.getStatus() == NeedStatus.REJETE || n.getStatus() == NeedStatus.REALISE) {
            throw new BusinessRuleException("Ce besoin n'est plus modifiable");
        }
        return n;
    }
}
