package com.civicollect.event;

import com.civicollect.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NeedEventListener {

    private final NotificationService notificationService;

    /** Exécuté dans la transaction de l'émetteur : notification et changement de statut sont atomiques. */
    @EventListener
    public void onStatusChanged(NeedStatusChangedEvent event) {
        notificationService.notifyStatusChange(event);
    }
}
