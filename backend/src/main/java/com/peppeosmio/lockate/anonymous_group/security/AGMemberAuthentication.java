package com.peppeosmio.lockate.anonymous_group.security;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AGMemberAuthentication implements Authentication {

    private final UUID agMemberId;
    private boolean authenticated = true;

    public AGMemberAuthentication(UUID agMemberId) {
        this.agMemberId = agMemberId;
    }

    public UUID getId() {
        return agMemberId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("AG_MEMBER"));
    }

    @Override
    @Nullable
    public Object getCredentials() {
        return null;
    }

    @Override
    @Nullable
    public Object getDetails() {
        return null;
    }

    @Override
    public UUID getPrincipal() {
        return agMemberId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated)
            throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return agMemberId.toString();
    }

    @Override
    public String toString() {
        return "AGMemberAuthentication(\"" + agMemberId + "\")";
    }
}
