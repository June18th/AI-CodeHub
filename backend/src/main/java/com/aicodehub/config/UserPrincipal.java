package com.aicodehub.config;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class UserPrincipal extends User {

    @Getter
    private final String role;
    @Getter
    private final String orgTags;

    public UserPrincipal(String username, String password,
                         Collection<? extends GrantedAuthority> authorities,
                         String role, String orgTags) {
        super(username, password, authorities);
        this.role = role;
        this.orgTags = orgTags;
    }

    public Long getUserId() {
        return Long.parseLong(getUsername());
    }
}
