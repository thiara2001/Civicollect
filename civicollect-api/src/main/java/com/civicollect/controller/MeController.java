package com.civicollect.web;

import com.civicollect.dto.AdminDtos.NotificationResponse;
import com.civicollect.dto.AuthDtos.*;
import com.civicollect.service.AuthService;
import com.civicollect.service.NotificationService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Mon compte")
@Hidden
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;
    private final NotificationService notificationService;

    @GetMapping
    public UserResponse me() {
        return authService.me();
    }

    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    @GetMapping("/notifications")
    public Page<NotificationResponse> notifications(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return notificationService.mine(page, size);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notificationService.unreadCount());
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id);
    }
}
