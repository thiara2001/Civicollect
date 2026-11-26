package com.civicollect.event;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedStatus;

/**
 * Publié à chaque changement de statut (ou demande de complément quand from == to).
 * Les écouteurs (notifications...) restent découplés des services métier.
 */
public record NeedStatusChangedEvent(Need need, NeedStatus from, NeedStatus to, String message) {

    public boolean isComplementRequest() {
        return from == to;
    }
}
