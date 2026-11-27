package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.NeedDtos.*;
import com.civicollect.event.NeedStatusChangedEvent;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.exception.ConflictException;
import com.civicollect.exception.ForbiddenOperationException;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.*;
import com.civicollect.security.AuthenticatedUser;
import com.civicollect.security.CurrentUserProvider;
import com.civicollect.util.PageUtils;
import com.civicollect.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** Cas d'usage côté citoyen : brouillon, soumission, soutien, commentaires, pièces jointes, consultation. */
@Service
@RequiredArgsConstructor
public class NeedService {

    private static final int MAX_ATTACHMENTS = 5;

    private final NeedRepository needs;
    private final CategoryRepository categories;
    private final ZoneRepository zones;
    private final UserRepository users;
    private final SupportRepository supports;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final NeedHistoryRepository historyRepository;
    private final EvaluationRepository evaluations;
    private final DecisionRepository decisions;
    private final NotificationRepository notifications;
    private final DuplicateProposalRepository duplicateProposals;
    private final HistoryService history;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;
    private final FileStorageService storage;
    private final DuplicateDetectionService duplicateService;

    // ======================= Création =======================

    /** RF8 : enregistrement d'un brouillon avant envoi. */
    @Transactional
    public NeedResponse saveDraft(NeedRequest r) {
        User user = users.findById(currentUser.require().getId()).orElseThrow();
        Need n = new Need();
        applyRequest(n, r);
        n.setRequester(user);
        n.setRequesterType(user.getRequesterType());
        n.setStatus(NeedStatus.BROUILLON);
        needs.save(n);
        history.record(n, "DRAFT_SAVED", null, NeedStatus.BROUILLON, null);
        return toResponse(n);
    }

    /** Création ou soumission directe d'une contribution (/api/contributions). */
    @Transactional
    public NeedResponse createContribution(ContributionRequest r) {
        User user = users.findById(currentUser.require().getId()).orElseThrow();
        Need n = new Need();
        applyRequest(n, new NeedRequest(r.title(), r.description(), r.categoryId(), r.zoneId(),
                r.address(), r.latitude(), r.longitude(), r.urgency()));
        n.setRequester(user);
        n.setRequesterType(user.getRequesterType() != null ? user.getRequesterType() : RequesterType.HABITANT);
        boolean asDraft = Boolean.TRUE.equals(r.draft());
        if (asDraft) {
            n.setStatus(NeedStatus.BROUILLON);
            needs.save(n);
            history.record(n, "DRAFT_SAVED", null, NeedStatus.BROUILLON, null);
            return toResponse(n);
        }
        if (n.getDescription() == null || n.getDescription().isBlank() || n.getCategory() == null) {
            throw new BusinessRuleException("La description et la catégorie sont obligatoires pour soumettre");
        }
        n.setStatus(NeedStatus.RECU);
        n.setSubmittedAt(Instant.now());
        needs.save(n);
        assignTrackingNumber(n);
        history.record(n, "SUBMITTED", null, NeedStatus.RECU, null);
        events.publishEvent(new NeedStatusChangedEvent(n, NeedStatus.BROUILLON, NeedStatus.RECU, null));
        return toResponse(n);
    }

    @Transactional
    public NeedResponse updateContribution(Long id, ContributionRequest r) {
        AuthenticatedUser au = currentUser.require();
        Need n = load(id);
        boolean owner = n.getRequester() != null && n.getRequester().getId().equals(au.getId());
        if (!owner && !au.isStaff()) {
            throw new NotFoundException("Contribution introuvable");
        }
        boolean ownerEditable = owner && (n.getStatus() == NeedStatus.BROUILLON || n.getStatus() == NeedStatus.RECU);
        boolean staffEditable = au.isStaff() && n.getStatus() != NeedStatus.BROUILLON
                && n.getStatus() != NeedStatus.REJETE && n.getStatus() != NeedStatus.REALISE;
        if (!ownerEditable && !staffEditable) {
            throw new BusinessRuleException("Cette contribution n'est plus modifiable");
        }
        applyRequest(n, new NeedRequest(r.title(), r.description(), r.categoryId(), r.zoneId(),
                r.address(), r.latitude(), r.longitude(), r.urgency()));
        if (n.isComplementRequested()) {
            n.setComplementRequested(false);
            history.record(n, "COMPLEMENT_PROVIDED", n.getStatus(), n.getStatus(), "Complément fourni par le demandeur");
        } else {
            history.record(n, "UPDATED", n.getStatus(), n.getStatus(), null);
        }
        return toResponse(n);
    }

    @Transactional
    public void deleteContribution(Long id) {
        AuthenticatedUser au = currentUser.require();
        Need n = load(id);
        boolean owner = n.getRequester() != null && n.getRequester().getId().equals(au.getId());
        boolean isAdmin = au.getRole() == Role.ADMIN;
        if (!owner && !isAdmin) {
            throw new ForbiddenOperationException("Suppression non autorisée");
        }
        if (!isAdmin && n.getStatus() != NeedStatus.BROUILLON && n.getStatus() != NeedStatus.RECU) {
            throw new BusinessRuleException("Seule une contribution en brouillon ou en attente peut être supprimée");
        }
        if (needs.existsByMergedIntoId(id)) {
            throw new BusinessRuleException("Impossible de supprimer une contribution consolidée référencée par d'autres");
        }
        duplicateProposals.deleteBySourceNeedIdOrTargetNeedId(id, id);
        supports.deleteByNeedId(id);
        comments.deleteByNeedId(id);
        attachments.deleteByNeedId(id);
        historyRepository.deleteByNeedId(id);
        evaluations.deleteByNeedId(id);
        decisions.deleteByNeedId(id);
        notifications.deleteByNeedId(id);
        needs.delete(n);
        audit.log("CONTRIBUTION_DELETED", "Need", id, n.getTrackingNumber());
    }

    @Transactional
    public NeedResponse updateDraft(Long id, NeedRequest r) {
        Need n = loadOwned(id);
        boolean draft = n.getStatus() == NeedStatus.BROUILLON;
        boolean complement = n.getStatus() == NeedStatus.RECU && n.isComplementRequested();
        if (!draft && !complement) {
            throw new BusinessRuleException("Ce besoin n'est plus modifiable");
        }
        applyRequest(n, r);
        if (complement) {
            n.setComplementRequested(false);
            history.record(n, "COMPLEMENT_PROVIDED", n.getStatus(), n.getStatus(), "Complément fourni par le demandeur");
        }
        return toResponse(n);
    }

    /** RF11 : envoi du brouillon, accusé de réception avec numéro de suivi. */
    @Transactional
    public NeedResponse submit(Long id) {
        Need n = loadOwned(id);
        if (n.getStatus() != NeedStatus.BROUILLON) {
            throw new BusinessRuleException("Seul un brouillon peut être soumis");
        }
        if (n.getDescription() == null || n.getDescription().isBlank() || n.getCategory() == null) {
            throw new BusinessRuleException("La description et la catégorie sont obligatoires pour soumettre");
        }
        n.setStatus(NeedStatus.RECU);
        n.setSubmittedAt(Instant.now());
        assignTrackingNumber(n);
        history.record(n, "SUBMITTED", NeedStatus.BROUILLON, NeedStatus.RECU, null);
        events.publishEvent(new NeedStatusChangedEvent(n, NeedStatus.BROUILLON, NeedStatus.RECU, null));
        return toResponse(n);
    }

    /** RF4 : soumission anonyme ou semi-anonyme (contact facultatif pour recevoir les notifications). */
    @Transactional
    public TrackingResponse submitAnonymous(AnonymousNeedRequest r) {
        if (r.website() != null && !r.website().isBlank()) {
            throw new BusinessRuleException("Soumission refusée");   // honeypot : seuls les robots remplissent ce champ
        }
        Need n = new Need();
        n.setTitle(r.title().trim());
        n.setDescription(r.description().trim());
        n.setCategory(findCategory(r.categoryId()));
        n.setZone(findZone(r.zoneId()));
        n.setAddress(r.address());
        n.setLatitude(r.latitude());
        n.setLongitude(r.longitude());
        n.setUrgency(r.urgency() != null ? r.urgency() : Urgency.MOYENNE);
        n.setAnonymous(true);
        n.setContactEmail(TextUtils.blankToNull(r.contactEmail()));
        n.setContactPhone(TextUtils.blankToNull(r.contactPhone()));
        n.setStatus(NeedStatus.RECU);
        n.setSubmittedAt(Instant.now());
        needs.save(n);
        assignTrackingNumber(n);
        history.record(n, "SUBMITTED_ANONYMOUS", null, NeedStatus.RECU, null);
        events.publishEvent(new NeedStatusChangedEvent(n, NeedStatus.BROUILLON, NeedStatus.RECU, null));
        return toTracking(n);
    }

    /** RF9 : saisie par un agent pour une personne non connectée. */
    @Transactional
    public NeedResponse enterByAgent(AgentEntryRequest r) {
        User agent = users.findById(currentUser.require().getId()).orElseThrow();
        Need n = new Need();
        n.setTitle(r.title().trim());
        n.setDescription(r.description().trim());
        n.setCategory(findCategory(r.categoryId()));
        n.setZone(findZone(r.zoneId()));
        n.setAddress(r.address());
        n.setLatitude(r.latitude());
        n.setLongitude(r.longitude());
        n.setUrgency(r.urgency() != null ? r.urgency() : Urgency.MOYENNE);
        n.setRequesterType(r.requesterType());
        n.setContactName(r.requesterName().trim());
        n.setContactPhone(TextUtils.blankToNull(r.requesterPhone()));
        n.setCreatedByAgent(agent);
        n.setStatus(NeedStatus.RECU);
        n.setSubmittedAt(Instant.now());
        needs.save(n);
        assignTrackingNumber(n);
        history.record(n, "ENTERED_BY_AGENT", null, NeedStatus.RECU, "Saisie assistée");
        events.publishEvent(new NeedStatusChangedEvent(n, NeedStatus.BROUILLON, NeedStatus.RECU, null));
        return toResponse(n);
    }

    // ======================= Consultation =======================

    @Transactional(readOnly = true)
    public NeedResponse get(Long id) {
        Need n = loadVisible(id);
        return toResponse(n);
    }

    @Transactional(readOnly = true)
    public Page<NeedResponse> mine(Pageable pageable) {
        AuthenticatedUser au = currentUser.require();
        Pageable p = PageUtils.sanitize(pageable, PageUtils.NEED_SORTS, Sort.by(Sort.Direction.DESC, "createdAt"));
        return toResponses(needs.findByRequesterId(au.getId(), p), au.getId());
    }

    /** RF21 : filtres + recherche textuelle. Les citoyens ne voient que les besoins validés et les leurs. */
    @Transactional(readOnly = true)
    public Page<NeedResponse> search(NeedFilter filter, Pageable pageable) {
        AuthenticatedUser au = currentUser.require();
        Pageable p = PageUtils.sanitize(pageable, PageUtils.NEED_SORTS, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Specification<Need> spec = Specification.where(visibility(au)).and(NeedSpecifications.filter(filter));
        return toResponses(needs.findAll(spec, p), au.getId());
    }

    /** Contributions validées (accessibles publiquement pour consultation). */
    @Transactional(readOnly = true)
    public Page<NeedResponse> publicValidated(NeedFilter filter, Pageable pageable) {
        Long uid = currentUser.find().map(AuthenticatedUser::getId).orElse(null);
        Pageable p = PageUtils.sanitize(pageable, PageUtils.NEED_SORTS, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Specification<Need> spec = Specification.where(NeedSpecifications.statusIn(NeedStatus.validated()))
                .and(NeedSpecifications.filter(filter));
        return toResponses(needs.findAll(spec, p), uid);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Long id) {
        Need n = loadVisible(id);
        boolean staff = currentUser.require().isStaff();
        return historyRepository.findByNeedIdOrderByCreatedAtAsc(n.getId()).stream()
                .map(h -> new HistoryEntry(h.getId(),
                        staff ? (h.getActor() != null ? h.getActor().getFullName() : "Système") : "Municipalité",
                        h.getAction(), h.getFromStatus(), h.getToStatus(), staff ? h.getDetails() : null, h.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidate> duplicates(Long id) {
        return duplicateService.findCandidates(load(id));
    }

    // ======================= Soutien (RF10, RG7) =======================

    @Transactional
    public SupportResponse support(Long needId) {
        AuthenticatedUser au = currentUser.require();
        Need n = loadVisible(needId);
        if (!n.getStatus().isValidated() || n.getMergedInto() != null) {
            throw new BusinessRuleException("Ce besoin ne peut pas être soutenu");
        }
        if (n.getRequester() != null && n.getRequester().getId().equals(au.getId())) {
            throw new BusinessRuleException("Vous ne pouvez pas soutenir votre propre besoin");
        }
        if (supports.existsByUserIdAndNeedId(au.getId(), needId)) {
            throw new ConflictException("Vous soutenez déjà ce besoin");
        }
        Support s = new Support();
        s.setNeed(n);
        s.setUser(users.getReferenceById(au.getId()));
        supports.save(s);
        history.record(n, "SUPPORTED", n.getStatus(), n.getStatus(), null);
        return new SupportResponse(needId, supports.countByNeedId(needId), true);
    }

    @Transactional
    public SupportResponse unsupport(Long needId) {
        AuthenticatedUser au = currentUser.require();
        Support s = supports.findByUserIdAndNeedId(au.getId(), needId)
                .orElseThrow(() -> new NotFoundException("Vous ne soutenez pas ce besoin"));
        supports.delete(s);
        return new SupportResponse(needId, supports.countByNeedId(needId) , false);
    }

    @Transactional(readOnly = true)
    public List<SupportEntryResponse> listSupports(Long needId) {
        loadVisible(needId);
        return supports.findByNeedIdOrderByCreatedAtDesc(needId).stream()
                .map(s -> new SupportEntryResponse(s.getId(), s.getUser().getId(), s.getUser().getFullName(), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportResponse supportCount(Long needId) {
        loadVisible(needId);
        Long uid = currentUser.find().map(AuthenticatedUser::getId).orElse(null);
        long count = supports.countByNeedId(needId);
        boolean mine = uid != null && supports.existsByUserIdAndNeedId(uid, needId);
        return new SupportResponse(needId, count, mine);
    }

    // ======================= Commentaires =======================

    @Transactional
    public CommentResponse addComment(Long needId, CommentRequest r) {
        AuthenticatedUser au = currentUser.require();
        Need n = loadVisible(needId);
        if (n.getStatus() == NeedStatus.BROUILLON) {
            throw new BusinessRuleException("Impossible de commenter un brouillon");
        }
        if (r.internal() && !au.isStaff()) {
            throw new ForbiddenOperationException("Seul le personnel peut ajouter une note interne");
        }
        Comment c = new Comment();
        c.setNeed(n);
        c.setAuthor(users.getReferenceById(au.getId()));
        c.setContent(r.content().trim());
        c.setInternalNote(r.internal());
        comments.save(c);
        return new CommentResponse(c.getId(), au.getDisplayName(), c.getContent(), c.isInternalNote(), c.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(Long needId) {
        boolean staff = currentUser.require().isStaff();
        loadVisible(needId);
        return comments.findByNeedIdOrderByCreatedAtAsc(needId).stream()
                .filter(c -> staff || !c.isInternalNote())
                .map(c -> new CommentResponse(c.getId(), c.getAuthor().getFullName(), c.getContent(),
                        c.isInternalNote(), c.getCreatedAt()))
                .toList();
    }

    // ======================= Pièces jointes (RF6) =======================

    @Transactional
    public AttachmentResponse addAttachment(Long needId, MultipartFile file) {
        AuthenticatedUser au = currentUser.require();
        Need n = load(needId);
        boolean owner = n.getRequester() != null && n.getRequester().getId().equals(au.getId());
        boolean editable = n.getStatus() == NeedStatus.BROUILLON || (n.getStatus() == NeedStatus.RECU && n.isComplementRequested());
        boolean staffEntry = au.isStaff() && n.getCreatedAt() != null && n.getStatus() != NeedStatus.REJETE;
        if (!((owner && editable) || staffEntry)) {
            throw new ForbiddenOperationException("Ajout de pièce jointe non autorisé sur ce besoin");
        }
        if (attachments.countByNeedId(needId) >= MAX_ATTACHMENTS) {
            throw new BusinessRuleException("Maximum " + MAX_ATTACHMENTS + " pièces jointes par besoin");
        }
        FileStorageService.StoredFile f = storage.store(file, needId);
        Attachment a = new Attachment();
        a.setNeed(n);
        a.setOriginalName(f.originalName());
        a.setContentType(f.contentType());
        a.setSizeBytes(f.size());
        a.setStoragePath(f.relativePath());
        attachments.save(a);
        history.record(n, "ATTACHMENT_ADDED", n.getStatus(), n.getStatus(), f.originalName());
        return toAttachment(a);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> attachments(Long needId) {
        loadVisible(needId);
        return attachments.findByNeedId(needId).stream().map(NeedService::toAttachment).toList();
    }

    @Transactional
    public AttachmentResponse addPhoto(Long needId, MultipartFile file) {
        String contentType = file != null ? file.getContentType() : null;
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new BusinessRuleException("Format de photo invalide (JPEG ou PNG uniquement)");
        }
        AuthenticatedUser au = currentUser.require();
        Need n = load(needId);
        boolean owner = n.getRequester() != null && n.getRequester().getId().equals(au.getId());
        boolean ownerEditable = owner && (n.getStatus() == NeedStatus.BROUILLON || n.getStatus() == NeedStatus.RECU);
        boolean staffEntry = au.isStaff() && n.getStatus() != NeedStatus.REJETE;
        if (!ownerEditable && !staffEntry) {
            throw new ForbiddenOperationException("Ajout de photo non autorisé sur cette contribution");
        }
        if (attachments.countByNeedId(needId) >= MAX_ATTACHMENTS) {
            throw new BusinessRuleException("Maximum " + MAX_ATTACHMENTS + " pièces jointes par besoin");
        }
        FileStorageService.StoredFile f = storage.store(file, needId);
        Attachment a = new Attachment();
        a.setNeed(n);
        a.setOriginalName(f.originalName());
        a.setContentType(f.contentType());
        a.setSizeBytes(f.size());
        a.setStoragePath(f.relativePath());
        attachments.save(a);
        history.record(n, "PHOTO_ADDED", n.getStatus(), n.getStatus(), f.originalName());
        return toAttachment(a);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> photos(Long needId) {
        loadVisible(needId);
        return attachments.findByNeedId(needId).stream()
                .filter(a -> a.getContentType() != null && a.getContentType().startsWith("image/"))
                .map(NeedService::toAttachment)
                .toList();
    }

    public record DownloadedFile(Resource resource, String name, String contentType) {}

    @Transactional(readOnly = true)
    public DownloadedFile download(Long needId, Long attachmentId) {
        loadVisible(needId);
        Attachment a = attachments.findById(attachmentId)
                .filter(x -> x.getNeed().getId().equals(needId))
                .orElseThrow(() -> new NotFoundException("Pièce jointe introuvable"));
        return new DownloadedFile(storage.load(a.getStoragePath()), a.getOriginalName(), a.getContentType());
    }

    // ======================= Utilitaires partagés =======================

    /** Conversion complète avec nombre de soutiens et indicateur « soutenu par moi ». */
    @Transactional(readOnly = true)
    public NeedResponse toResponse(Need n) {
        Long uid = currentUser.find().map(AuthenticatedUser::getId).orElse(null);
        long count = supports.countByNeedId(n.getId());
        boolean mine = uid != null && supports.existsByUserIdAndNeedId(uid, n.getId());
        return NeedMapper.toResponse(n, count, mine);
    }

    public Map<Long, Long> supportCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return supports.countByNeedIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private Page<NeedResponse> toResponses(Page<Need> page, Long userId) {
        List<Long> ids = page.getContent().stream().map(Need::getId).toList();
        Map<Long, Long> counts = supportCounts(ids);
        Set<Long> mine = (ids.isEmpty() || userId == null) ? Set.of() : new HashSet<>(supports.findSupportedNeedIds(userId, ids));
        return page.map(n -> NeedMapper.toResponse(n, counts.getOrDefault(n.getId(), 0L), mine.contains(n.getId())));
    }

    private Specification<Need> visibility(AuthenticatedUser au) {
        if (au.isStaff()) return NeedSpecifications.staffVisible();
        return NeedSpecifications.statusIn(NeedStatus.validated()).or(NeedSpecifications.ownedBy(au.getId()));
    }

    private static AttachmentResponse toAttachment(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt());
    }

    public static TrackingResponse toTracking(Need n) {
        Need ref = n.getMergedInto() != null ? n.getMergedInto() : n;   // un besoin fusionné suit le statut du consolidé
        return new TrackingResponse(n.getTrackingNumber(), n.getTitle(), ref.getStatus(), ref.getStatus().getLabel(),
                ref.getStatus() == NeedStatus.REJETE ? ref.getRejectionReason() : null,
                n.isComplementRequested(), n.getSubmittedAt(),
                ref.getUpdatedAt() != null ? ref.getUpdatedAt() : n.getUpdatedAt());
    }

    static void assignTrackingNumber(Need n) {
        if (n.getTrackingNumber() == null) {
            n.setTrackingNumber("CC-" + Year.now(ZoneOffset.UTC).getValue() + "-" + String.format("%06d", n.getId()));
        }
    }

    private void applyRequest(Need n, NeedRequest r) {
        n.setTitle(r.title().trim());
        n.setDescription(TextUtils.blankToNull(r.description()));
        n.setCategory(findCategory(r.categoryId()));
        n.setZone(findZone(r.zoneId()));
        n.setAddress(r.address());
        n.setLatitude(r.latitude());
        n.setLongitude(r.longitude());
        n.setUrgency(r.urgency() != null ? r.urgency() : Urgency.MOYENNE);
    }

    private Category findCategory(Long id) {
        if (id == null) return null;
        return categories.findById(id).filter(Category::isActive)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable"));
    }

    private Zone findZone(Long id) {
        if (id == null) return null;
        return zones.findById(id).orElseThrow(() -> new NotFoundException("Quartier/zone introuvable"));
    }

    Need load(Long id) {
        return needs.findById(id).orElseThrow(() -> new NotFoundException("Besoin introuvable"));
    }

    private Need loadOwned(Long id) {
        AuthenticatedUser au = currentUser.require();
        Need n = load(id);
        if (n.getRequester() == null || !n.getRequester().getId().equals(au.getId())) {
            throw new NotFoundException("Besoin introuvable");   // ne révèle pas l'existence du besoin d'autrui
        }
        return n;
    }

    /** Un besoin est visible du personnel (hors brouillons), de son auteur, et de tous une fois validé. */
    private Need loadVisible(Long id) {
        AuthenticatedUser au = currentUser.require();
        Need n = load(id);
        boolean owner = n.getRequester() != null && n.getRequester().getId().equals(au.getId());
        boolean ok = au.isStaff()
                ? n.getStatus() != NeedStatus.BROUILLON
                : owner || (n.getStatus().isValidated() && n.getMergedInto() == null);
        if (!ok) throw new NotFoundException("Besoin introuvable");
        return n;
    }
}
