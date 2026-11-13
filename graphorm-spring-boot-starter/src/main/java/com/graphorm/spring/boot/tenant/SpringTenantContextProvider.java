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

import java.util.Objects;
import java.util.Optional;

/**
 * Spring-aware {@link TenantContextProvider} bridging active thread context ({@link TenantContext}),
 * Spring Security principals (JWT claims, OAuth2 attributes, custom principals),
 * and incoming HTTP request headers (e.g. {@code X-Tenant-ID}).
 * <p>
 * <b>Precedence Rules:</b>
 * <ol>
 *   <li>Trusted programmatic tenant overrides ({@link TenantContext#runAsTenant}, {@link TenantContext#supplyAsTenant})</li>
 *   <li>Authenticated Security Context tenant identity (JWT claims, OAuth2 attributes)</li>
 *   <li>Validated HTTP Request Header (via unified {@link TenantHeaderAuthorizer})</li>
 *   <li>Explicit fallback to {@link TenantContext#getTenantId()}</li>
 * </ol>
 */
public class SpringTenantContextProvider implements TenantContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringTenantContextProvider.class);

    private final GraphOrmProperties.TenantProperties properties;
    private final TenantAuthorizationStrategy authorizationStrategy;

    public SpringTenantContextProvider(GraphOrmProperties.TenantProperties properties) {
        this(properties, null);
    }

    public SpringTenantContextProvider(
            GraphOrmProperties.TenantProperties properties,
            TenantAuthorizationStrategy authorizationStrategy
    ) {
        this.properties = Objects.requireNonNull(properties, "TenantProperties must not be null");
        this.authorizationStrategy = authorizationStrategy;
    }

    @Override
    public Optional<String> getCurrentTenantId() {
        // 1. Programmatic tenant override (e.g. TenantContext.runAsTenant / supplyAsTenant)
        // is explicitly invoked by trusted application code and takes highest precedence
        Optional<String> programmaticTenant = TenantContext.getProgrammaticTenantId();
        if (programmaticTenant.isPresent()) {
            return programmaticTenant;
        }

        // 2. Authenticated identity from Spring Security Context is authoritative
        Optional<String> securityTenant = resolveFromSecurityContext();
        if (securityTenant.isPresent() && !properties.isAllowHeaderOverride()) {
            return securityTenant;
        }

        // 3. Resolve from HTTP Request Header (untrusted user input - requires validation/authorization)
        if (properties.isHeaderEnabled()) {
            Optional<String> headerTenant = resolveFromRequestHeader(securityTenant);
            if (headerTenant.isPresent()) {
                return headerTenant;
            }
        }

        // If authenticated security context was present but header override was allowed and not supplied/authorized,
        // fall back to the authenticated security tenant
        if (securityTenant.isPresent()) {
            return securityTenant;
        }

        // 4. ThreadLocal TenantContext fallback (if set programmatically via TenantContext.setTenantId)
        Optional<String> explicitTenant = TenantContext.getTenantId();
        if (explicitTenant.isPresent()) {
            return explicitTenant;
        }

        return Optional.empty();
    }

    public GraphOrmProperties.TenantProperties properties() {
        return properties;
    }

    public TenantAuthorizationStrategy authorizationStrategy() {
        return authorizationStrategy;
    }

    private Optional<String> resolveFromSecurityContext() {
        return TenantHeaderAuthorizer.resolveSecurityTenant(properties);
    }

    private Optional<String> resolveFromRequestHeader(Optional<String> securityTenant) {
        try {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
                String headerName = properties.getHeaderName() != null ? properties.getHeaderName() : "X-Tenant-ID";
                String headerVal = servletAttributes.getRequest().getHeader(headerName);
                Authentication auth = null;
                try {
                    SecurityContext context = SecurityContextHolder.getContext();
                    if (context != null) {
                        auth = context.getAuthentication();
                    }
                } catch (Exception ignored) {
                }
                return TenantHeaderAuthorizer.authorize(
                        headerVal,
                        securityTenant,
                        auth,
                        properties,
                        authorizationStrategy
                );
            }
        } catch (NoClassDefFoundError | Exception e) {
            log.trace("Unable to resolve tenant ID from request header: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
