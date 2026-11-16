package com.campusbridge.security;

import com.campusbridge.entity.User;
import com.campusbridge.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final UserRole role;
    private final boolean enabled;
    private String password;

    public UserPrincipal(User user) {
        userId = user.getId();
        username = user.getUsername();
        role = user.getRole();
        enabled = Integer.valueOf(1).equals(user.getEnabled());
        password = user.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
