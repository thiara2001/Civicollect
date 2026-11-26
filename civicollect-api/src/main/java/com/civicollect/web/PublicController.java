package com.civicollect.web;

import com.civicollect.dto.NeedDtos.*;
import com.civicollect.service.NeedService;
import com.civicollect.service.PublicService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Public (sans authentification)")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicService publicService;
    private final NeedService needService;

    /** RF4 : soumission anonyme ou semi-anonyme (limitée en débit + champ honeypot). */
    @PostMapping("/needs/anonymous")
    public ResponseEntity<TrackingResponse> submitAnonymous(@Valid @RequestBody AnonymousNeedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(needService.submitAnonymous(request));
    }

    /** RF30 : besoins retenus, planifiés, en cours et réalisés. */
    @GetMapping("/needs")
    public Page<PublicNeedResponse> needs(NeedFilter filter, Pageable pageable) {
        return publicService.publicNeeds(filter, pageable);
    }

    @GetMapping("/needs/map")
    public List<MapPoint> map(NeedFilter filter) {
        return publicService.publicMap(filter);
    }

    /** RF11 : suivi par numéro de suivi. */
    @GetMapping("/track/{trackingNumber}")
    public TrackingResponse track(@PathVariable String trackingNumber) {
        return publicService.track(trackingNumber);
    }
}
