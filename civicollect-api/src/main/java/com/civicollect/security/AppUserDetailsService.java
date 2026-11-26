package com.civicollect.security;

import com.civicollect.domain.User;
import com.civicollect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    /** L'identifiant de connexion est l'e-mail ou le numéro de téléphone (RF1). */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User u = users.findByEmailIgnoreCaseOrPhone(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
        return toPrincipal(u);
    }

    public AuthenticatedUser loadById(Long id) {
        User u = users.findById(id).orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
        return toPrincipal(u);
    }

    public static AuthenticatedUser toPrincipal(User u) {
        String username = u.getEmail() != null ? u.getEmail() : u.getPhone();
        return new AuthenticatedUser(u.getId(), username, u.getPasswordHash(), u.getFullName(),
                u.getRole(), u.isEnabled(), u.isHabilitated());
    }
}
