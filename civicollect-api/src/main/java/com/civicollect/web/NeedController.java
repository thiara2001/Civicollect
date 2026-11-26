package com.civicollect.web;

import com.civicollect.dto.NeedDtos.*;
import com.civicollect.service.NeedService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Besoins")
@RestController
@RequestMapping("/api/needs")
@RequiredArgsConstructor
public class NeedController {

    private final NeedService needService;

    // ---- dépôt (habitants et associations)

    @PostMapping
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public ResponseEntity<NeedResponse> createDraft(@Valid @RequestBody NeedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.saveDraft(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public NeedResponse update(@PathVariable Long id, @Valid @RequestBody NeedRequest request) {
        return needService.updateDraft(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public NeedResponse submit(@PathVariable Long id) {
        return needService.submit(id);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public Page<NeedResponse> mine(Pageable pageable) {
        return needService.mine(pageable);
    }

    /** RF9 : saisie assistée par un agent. */
    @PostMapping("/agent-entry")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<NeedResponse> agentEntry(@Valid @RequestBody AgentEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.enterByAgent(request));
    }

    // ---- consultation / recherche (RF21)

    @GetMapping
    public Page<NeedResponse> search(NeedFilter filter, Pageable pageable) {
        return needService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    public NeedResponse get(@PathVariable Long id) {
        return needService.get(id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@PathVariable Long id) {
        return needService.history(id);
    }

    @GetMapping("/{id}/duplicates")
    @PreAuthorize("hasAnyRole('AGENT','ELU','ADMIN')")
    public List<DuplicateCandidate> duplicates(@PathVariable Long id) {
        return needService.duplicates(id);
    }

    // ---- soutien (RF10, RG7)

    @PostMapping("/{id}/support")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public SupportResponse support(@PathVariable Long id) {
        return needService.support(id);
    }

    @DeleteMapping("/{id}/support")
    @PreAuthorize("hasAnyRole('HABITANT','ASSOCIATION')")
    public SupportResponse unsupport(@PathVariable Long id) {
        return needService.unsupport(id);
    }

    // ---- commentaires

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return needService.comments(id);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.addComment(id, request));
    }

    // ---- pièces jointes (RF6)

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.addAttachment(id, file));
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentResponse> attachments(@PathVariable Long id) {
        return needService.attachments(id);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        NeedService.DownloadedFile f = needService.download(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.resource());
    }
}
