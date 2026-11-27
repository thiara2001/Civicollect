package com.civicollect.controller;

import com.civicollect.dto.AdminDtos.NotificationResponse;
import com.civicollect.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Notifications utilisateur – /api/notifications (RF29).
 * Chaque utilisateur ne voit que ses propres notifications (sécurité dans le service).
 */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationsController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.mine(page, size);
    }

    @GetMapping("/non-lues")
    public Page<NotificationResponse> nonLues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.unread(page, size);
    }

    @PatchMapping("/{id}/lue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marquerLue(@PathVariable Long id) {
        notificationService.markRead(id);
    }

    @PatchMapping("/lues")
    public java.util.Map<String, Object> marquerToutesLues() {
        int count = notificationService.markAllRead();
        return java.util.Map.of("marked", count);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }
}
