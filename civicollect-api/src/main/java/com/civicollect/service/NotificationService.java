package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.dto.AdminDtos.NotificationResponse;
import com.civicollect.event.NeedStatusChangedEvent;
import com.civicollect.exception.NotFoundException;
import com.civicollect.repository.NotificationRepository;
import com.civicollect.repository.NotificationTemplateRepository;
import com.civicollect.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Notifications au demandeur à chaque changement de statut (RF29) à partir de modèles paramétrables (RF35). */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationTemplateRepository templates;
    private final List<MessageChannel> channels;
    private final CurrentUserProvider currentUser;

    @Transactional
    public void notifyStatusChange(NeedStatusChangedEvent event) {
        Need need = event.need();
        String subject;
        String body;
        if (event.isComplementRequest()) {
            subject = "Complément d'information requis";
            body = "Votre demande " + nz(need.getTrackingNumber()) + " nécessite un complément : " + nz(event.message());
        } else {
            NotificationTemplate t = templates.findByStatus(event.to()).orElse(null);
            subject = render(t != null ? t.getSubject() : "Mise à jour de votre demande {trackingNumber}", need, event);
            body = render(t != null ? t.getBody()
                    : "Votre demande « {title} » est désormais : {status}. {reason}", need, event);
        }
        notifyRequester(need, subject, body);
    }

    /** Notifie le demandeur d'origine d'un besoin (utilisateur inscrit ou contact laissé en soumission anonyme). */
    @Transactional
    public void notifyRequester(Need need, String subject, String body) {
        User user = need.getRequester();
        String email = user != null ? user.getEmail() : need.getContactEmail();
        String phone = user != null ? user.getPhone() : need.getContactPhone();
        if (user != null) {
            save(user, need, NotificationChannel.IN_APP, null, subject, body, true);
        }
        for (MessageChannel ch : channels) {
            String target = switch (ch.channel()) {
                case EMAIL -> email;
                case SMS -> phone;
                default -> null;
            };
            if (target == null) continue;
            boolean sent = false;
            try {
                ch.send(target, subject, body);
                sent = true;
            } catch (RuntimeException ex) {
                log.warn("Échec d'envoi {} : {}", ch.channel(), ex.getMessage());
            }
            save(user, need, ch.channel(), target, subject, body, sent);
        }
    }

    /** Envoi direct hors besoin (réinitialisation de mot de passe) : non persisté. */
    public void sendDirect(User user, String subject, String body) {
        for (MessageChannel ch : channels) {
            String target = ch.channel() == NotificationChannel.EMAIL ? user.getEmail()
                    : ch.channel() == NotificationChannel.SMS ? user.getPhone() : null;
            if (target == null) continue;
            try {
                ch.send(target, subject, body);
            } catch (RuntimeException ex) {
                log.warn("Échec d'envoi {} : {}", ch.channel(), ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> mine(int page, int size) {
        Long userId = currentUser.require().getId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return notifications.findByRecipientIdAndChannelOrderByCreatedAtDesc(userId, NotificationChannel.IN_APP, pageable)
                .map(NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> unread(int page, int size) {
        Long userId = currentUser.require().getId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return notifications.findByRecipientIdAndChannelAndReadFalseOrderByCreatedAtDesc(
                        userId, NotificationChannel.IN_APP, pageable)
                .map(NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientIdAndChannelAndReadFalse(currentUser.require().getId(), NotificationChannel.IN_APP);
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = loadOwned(id);
        n.setRead(true);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(currentUser.require().getId(), NotificationChannel.IN_APP);
    }

    @Transactional
    public void delete(Long id) {
        Notification n = loadOwned(id);
        notifications.delete(n);
    }

    private Notification loadOwned(Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> new NotFoundException("Notification introuvable"));
        if (n.getRecipient() == null || !n.getRecipient().getId().equals(currentUser.require().getId())) {
            throw new NotFoundException("Notification introuvable");
        }
        return n;
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getSubject(), n.getMessage(),
                n.getNeed() != null ? n.getNeed().getId() : null, n.isRead(), n.getCreatedAt());
    }

    private void save(User user, Need need, NotificationChannel channel, String contact, String subject, String body, boolean sent) {
        Notification n = new Notification();
        n.setRecipient(user);
        n.setRecipientContact(contact);
        n.setNeed(need);
        n.setChannel(channel);
        n.setSubject(truncate(subject, 255));
        n.setMessage(truncate(body, 2000));
        n.setSent(sent);
        notifications.save(n);
    }

    static String render(String template, Need need, NeedStatusChangedEvent e) {
        return template
                .replace("{trackingNumber}", nz(need.getTrackingNumber()))
                .replace("{title}", nz(need.getTitle()))
                .replace("{status}", e.to().getLabel())
                .replace("{reason}", nz(e.message()))
                .trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
