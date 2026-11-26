package com.civicollect.security;

import com.civicollect.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Principal Spring Security : instantané léger de l'utilisateur connecté (pas d'entité JPA exposée). */
public class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final Role role;
    private final boolean enabled;
    private final boolean habilitated;

    public AuthenticatedUser(Long id, String username, String password, String displayName,
                             Role role, boolean enabled, boolean habilitated) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
        this.habilitated = habilitated;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isHabilitated() { return habilitated; }
    public boolean isStaff() { return role.isStaff(); }

    /** RG4 / RG5 : administrateur, ou élu habilité. */
    public boolean canManagePriorities() {
        return role == Role.ADMIN || (role == Role.ELU && habilitated);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
