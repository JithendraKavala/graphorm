package com.graphorm.spring.boot.tenant;

import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Unified authorizer and security claim resolver for untrusted HTTP header tenant identifiers.
 * Guarantees consistent validation and authorization rules between {@link TenantHeaderFilter}
 * and {@link SpringTenantContextProvider}.
 */
public final class TenantHeaderAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(TenantHeaderAuthorizer.class);

    private TenantHeaderAuthorizer() {}

    /**
     * Resolves the authoritative tenant claim from the active Spring Security context, if any.
     *
     * @param properties the tenant configuration properties
     * @return an {@link Optional} containing the tenant claim, or empty if absent/unauthenticated
     */
    public static Optional<String> resolveSecurityTenant(GraphOrmProperties.TenantProperties properties) {
        try {
            SecurityContext context = SecurityContextHolder.getContext();
            if (context == null) {
                return Optional.empty();
            }
            Authentication auth = context.getAuthentication();
            return resolveSecurityTenant(auth, properties);
        } catch (NoClassDefFoundError | Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the authoritative tenant claim from the given authentication, if any.
     *
     * @param auth       the Spring Security authentication
     * @param properties the tenant configuration properties
     * @return an {@link Optional} containing the tenant claim, or empty if absent/unauthenticated
     */
    public static Optional<String> resolveSecurityTenant(
            Authentication auth,
            GraphOrmProperties.TenantProperties properties
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal == null) {
            return Optional.empty();
        }

        String claimName = properties != null && properties.getJwtClaim() != null
                ? properties.getJwtClaim()
                : "tenant_id";

        // Check if principal is a Map (common in tokens or custom details)
        if (principal instanceof Map<?, ?> map) {
            Object val = map.get(claimName);
            if (val != null) {
                return Optional.of(String.valueOf(val).trim());
            }
        }

        // Check if principal has getClaims() (e.g. Jwt)
        try {
            Method getClaims = principal.getClass().getMethod("getClaims");
            Object claimsObj = getClaims.invoke(principal);
            if (claimsObj instanceof Map<?, ?> claimsMap) {
                Object val = claimsMap.get(claimName);
                if (val != null) {
                    return Optional.of(String.valueOf(val).trim());
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            log.trace("Error invoking getClaims() on principal: {}", e.getMessage());
        }

        // Check if principal has getAttributes() (e.g. OAuth2AuthenticatedPrincipal)
        try {
            Method getAttributes = principal.getClass().getMethod("getAttributes");
            Object attrsObj = getAttributes.invoke(principal);
            if (attrsObj instanceof Map<?, ?> attrsMap) {
                Object val = attrsMap.get(claimName);
                if (val != null) {
                    return Optional.of(String.valueOf(val).trim());
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            log.trace("Error invoking getAttributes() on principal: {}", e.getMessage());
        }

        // Check if principal has getClaimAsString(String)
        try {
            Method getClaim = principal.getClass().getMethod("getClaimAsString", String.class);
            Object val = getClaim.invoke(principal, claimName);
            if (val != null) {
                return Optional.of(String.valueOf(val).trim());
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            log.trace("Error invoking getClaimAsString() on principal: {}", e.getMessage());
        }

        // Check auth.getDetails()
        try {
            Object details = auth.getDetails();
            if (details instanceof Map<?, ?> detailsMap) {
                Object val = detailsMap.get(claimName);
                if (val != null) {
                    return Optional.of(String.valueOf(val).trim());
                }
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    /**
     * Unified authorization check for an HTTP header tenant candidate.
     *
     * @param candidateTenant      the tenant candidate from the HTTP request header
     * @param securityTenant       the tenant claim from the authenticated security context, if any
     * @param authentication       the current authentication, if any
     * @param properties           the tenant configuration properties
     * @param authorizationStrategy the tenant authorization strategy, if configured
     * @return an {@link Optional} containing the authorized tenant ID, or empty if rejected
     */
    public static Optional<String> authorize(
            String candidateTenant,
            Optional<String> securityTenant,
            Authentication authentication,
            GraphOrmProperties.TenantProperties properties,
            TenantAuthorizationStrategy authorizationStrategy
    ) {
        if (properties == null || !properties.isHeaderEnabled() || candidateTenant == null || candidateTenant.isBlank()) {
            return Optional.empty();
        }

        String candidate = candidateTenant.trim();

        // 1. If security context has an authoritative tenant claim:
        if (securityTenant != null && securityTenant.isPresent() && !securityTenant.get().isBlank()) {
            String authenticatedTenant = securityTenant.get();
            if (candidate.equals(authenticatedTenant)) {
                return Optional.of(candidate);
            }

            // Header differs from the authenticated security tenant: requires allowHeaderOverride
            if (!properties.isAllowHeaderOverride()) {
                log.debug("Header tenant '{}' ignored because authenticated tenant '{}' is authoritative",
                        candidate, authenticatedTenant);
                return Optional.empty();
            }

            // allowHeaderOverride is enabled: header must be explicitly authorized by TenantAuthorizationStrategy
            if (authorizationStrategy != null) {
                if (authorizationStrategy.isTenantAuthorized(candidate, authentication)) {
                    return Optional.of(candidate);
                }
                log.warn("Header tenant override '{}' rejected by TenantAuthorizationStrategy for principal '{}'",
                        candidate, authentication != null ? authentication.getName() : "anonymous");
                return Optional.empty();
            }

            if (properties.isRequireHeaderAuthorization()) {
                log.warn("Header tenant override '{}' rejected: requireHeaderAuthorization is true but no TenantAuthorizationStrategy bean is configured",
                        candidate);
                return Optional.empty();
            }

            return Optional.of(candidate);
        }

        // 2. Authenticated user has NO tenant claim, or unauthenticated:
        if (authorizationStrategy != null) {
            if (authorizationStrategy.isTenantAuthorized(candidate, authentication)) {
                return Optional.of(candidate);
            }
            log.warn("Header tenant '{}' rejected by TenantAuthorizationStrategy for principal '{}'",
                    candidate, authentication != null ? authentication.getName() : "anonymous");
            return Optional.empty();
        }

        // No TenantAuthorizationStrategy configured: check requireHeaderAuthorization
        if (properties.isRequireHeaderAuthorization()) {
            log.warn("Header tenant '{}' rejected: requireHeaderAuthorization is true but no TenantAuthorizationStrategy bean is configured. HTTP headers are untrusted user input.",
                    candidate);
            return Optional.empty();
        }

        return Optional.of(candidate);
    }
}
