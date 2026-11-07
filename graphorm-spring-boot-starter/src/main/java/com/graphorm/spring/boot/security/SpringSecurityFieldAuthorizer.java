package com.graphorm.spring.boot.security;

import com.graphorm.graphql.security.FieldAuthorizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Security integration for field-level authorization (TASK-15.2).
 * Reads the authenticated user's principal and granted authorities from {@link SecurityContextHolder}.
 */
public class SpringSecurityFieldAuthorizer implements FieldAuthorizer {

    @Override
    public boolean isAuthorized(List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        Set<String> userRoles = new HashSet<>();
        for (GrantedAuthority ga : authorities) {
            String role = ga.getAuthority();
            if (role != null) {
                String clean = role.toUpperCase();
                userRoles.add(clean);
                if (clean.startsWith("ROLE_")) {
                    userRoles.add(clean.substring(5));
                }
            }
        }

        for (String req : requiredRoles) {
            String cleanReq = req.toUpperCase();
            if (cleanReq.startsWith("ROLE_")) {
                cleanReq = cleanReq.substring(5);
            }
            if (userRoles.contains(cleanReq) || userRoles.contains("ROLE_" + cleanReq)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Collection<String> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptyList();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
