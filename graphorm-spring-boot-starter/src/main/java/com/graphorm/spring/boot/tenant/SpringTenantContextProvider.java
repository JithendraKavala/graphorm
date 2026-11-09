package com.graphorm.spring.boot.tenant;

import com.graphorm.core.tenant.TenantContext;
import com.graphorm.core.tenant.TenantContextProvider;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring-aware {@link TenantContextProvider} bridging active thread context ({@link TenantContext}),
 * Spring Security principals (JWT claims, OAuth2 attributes, custom principals),
 * and incoming HTTP request headers (e.g. {@code X-Tenant-ID}).
 */
public class SpringTenantContextProvider implements TenantContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringTenantContextProvider.class);

    private final GraphOrmProperties.TenantProperties properties;

    public SpringTenantContextProvider(GraphOrmProperties.TenantProperties properties) {
        this.properties = Objects.requireNonNull(properties, "TenantProperties must not be null");
    }

    @Override
    public Optional<String> getCurrentTenantId() {
        // 1. ThreadLocal TenantContext takes first priority
        Optional<String> explicitTenant = TenantContext.getTenantId();
        if (explicitTenant.isPresent()) {
            return explicitTenant;
        }

        // 2. Resolve from Spring Security Context if present
        Optional<String> securityTenant = resolveFromSecurityContext();
        if (securityTenant.isPresent()) {
            return securityTenant;
        }

        // 3. Resolve from HTTP Request Header if web context is active
        Optional<String> headerTenant = resolveFromRequestHeader();
        if (headerTenant.isPresent()) {
            return headerTenant;
        }

        return Optional.empty();
    }

    public GraphOrmProperties.TenantProperties properties() {
        return properties;
    }

    private Optional<String> resolveFromSecurityContext() {
        try {
            SecurityContext context = SecurityContextHolder.getContext();
            if (context == null) {
                return Optional.empty();
            }
            Authentication auth = context.getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }

            Object principal = auth.getPrincipal();
            if (principal == null) {
                return Optional.empty();
            }

            String claimName = properties.getJwtClaim() != null ? properties.getJwtClaim() : "tenant_id";

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
            }

            // Check if principal has getClaimAsString(String)
            try {
                Method getClaim = principal.getClass().getMethod("getClaimAsString", String.class);
                Object val = getClaim.invoke(principal, claimName);
                if (val != null) {
                    return Optional.of(String.valueOf(val).trim());
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Check auth.getDetails()
            Object details = auth.getDetails();
            if (details instanceof Map<?, ?> detailsMap) {
                Object val = detailsMap.get(claimName);
                if (val != null) {
                    return Optional.of(String.valueOf(val).trim());
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            log.trace("Unable to resolve tenant ID from security context: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> resolveFromRequestHeader() {
        try {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
                String headerName = properties.getHeaderName() != null ? properties.getHeaderName() : "X-Tenant-ID";
                String headerVal = servletAttributes.getRequest().getHeader(headerName);
                if (headerVal != null && !headerVal.isBlank()) {
                    return Optional.of(headerVal.trim());
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            log.trace("Unable to resolve tenant ID from request header: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
