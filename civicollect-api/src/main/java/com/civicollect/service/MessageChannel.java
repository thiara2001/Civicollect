package com.civicollect.service;

import com.civicollect.domain.NotificationChannel;

/**
 * Canal de diffusion (pattern Strategy). Pour ajouter un canal (WhatsApp, USSD...) il suffit
 * d'ajouter un @Component : aucune modification du code existant (évolutivité, RNF).
 */
public interface MessageChannel {
    NotificationChannel channel();

    void send(String recipient, String subject, String body);
}
