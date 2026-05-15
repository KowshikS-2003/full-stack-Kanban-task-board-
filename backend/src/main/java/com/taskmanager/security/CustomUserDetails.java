package com.taskmanager.security;

import com.taskmanager.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final Long   userId;
    private final String username;
    private final String password;

    /** Used by UserService (production path). */
    public CustomUserDetails(User user) {
        this.userId   = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
    }

    /** Used by WithMockCustomUserSecurityContextFactory (test path). */
    public CustomUserDetails(Long userId, String username) {
        this.userId   = userId;
        this.username = username;
        this.password = "";
    }

    public Long getUserId() { return userId; }

    @Override public String getUsername()    { return username; }
    @Override public String getPassword()    { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return Collections.emptyList(); }
    @Override public boolean isAccountNonExpired()   { return true; }
    @Override public boolean isAccountNonLocked()    { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()             { return true; }
}
