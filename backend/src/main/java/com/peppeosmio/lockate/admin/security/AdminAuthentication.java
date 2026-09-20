package com.peppeosmio.lockate.admin.security;

import java.util.Collection;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AdminAuthentication implements Authentication {

  private boolean authenticated = true;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ADMIN"));
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
  public String getPrincipal() {
    return "admin";
  }

  @Override
  public boolean isAuthenticated() {
    return authenticated;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    this.authenticated = isAuthenticated;
  }

  @Override
  public String getName() {
    return "admin";
  }
}
