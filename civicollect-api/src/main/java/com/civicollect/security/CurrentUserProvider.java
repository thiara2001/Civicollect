package com.civicollect.security;

import com.civicollect.domain.User;
import com.civicollect.exception.ForbiddenOperationException;
import com.civicollect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Point d'accès unique à l'utilisateur courant (évite de disperser SecurityContextHolder dans les services). */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository users;

    public Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new ForbiddenOperationException("Authentification requise"));
    }

    /** Référence JPA de l'utilisateur courant (proxy, sans requête tant qu'aucun champ n'est lu). */
    public User requireUser() {
        return users.getReferenceById(require().getId());
    }
}
