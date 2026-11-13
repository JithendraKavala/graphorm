package com.graphorm.spring.boot.tenant;

import com.graphorm.core.tenant.TenantContext;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * HTTP filter extracting the tenant identifier from the configured request header (default {@code X-Tenant-ID})
 * and binding it to a scoped {@link TenantContext} for the duration of the HTTP request.
 * <p>
 * <b>Security Requirements:</b>
 * <ul>
 *   <li>HTTP request headers are untrusted client inputs. Header-based tenant selection requires explicit opt-in
 *       via {@code graphorm.tenant.header-enabled=true} and verification via {@link TenantAuthorizationStrategy}.</li>
 *   <li>An authenticated tenant identity in the security context takes precedence over headers unless
 *       {@code allowHeaderOverride} is explicitly configured and authorized.</li>
 *   <li>Active trusted programmatic overrides are never overwritten by HTTP headers.</li>
 *   <li>Previous tenant contexts are preserved and restored upon filter completion, even on exceptions.</li>
 * </ul>
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private final GraphOrmProperties.TenantProperties properties;
    private final TenantAuthorizationStrategy authorizationStrategy;
    private final String headerName;

    /**
     * Legacy constructor retained for backwards compatibility and unit testing.
     * <p>
     * <b>Security Note:</b> This constructor enables header extraction but sets
     * {@code requireHeaderAuthorization = false} and configures no {@link TenantAuthorizationStrategy}.
     * It should not be used in production applications where tenant spoofing must be guarded.
     *
     * @param headerName the request header name (defaults to {@code X-Tenant-ID} if null or blank)
     * @deprecated since 0.1.0, scheduled for removal. Prefer
     *             {@link #TenantHeaderFilter(GraphOrmProperties.TenantProperties, TenantAuthorizationStrategy)}
     *             to enforce proper tenant authorization policies.
     */
    @Deprecated(since = "0.1.0", forRemoval = true)
    public TenantHeaderFilter(String headerName) {
        this.headerName = headerName != null && !headerName.isBlank() ? headerName : "X-Tenant-ID";
        GraphOrmProperties.TenantProperties props = new GraphOrmProperties.TenantProperties();
        props.setHeaderName(this.headerName);
        props.setHeaderEnabled(true);
        props.setRequireHeaderAuthorization(false);
        this.properties = props;
        this.authorizationStrategy = null;
    }

    public TenantHeaderFilter(GraphOrmProperties.TenantProperties properties) {
        this(properties, null);
    }

    public TenantHeaderFilter(
            GraphOrmProperties.TenantProperties properties,
            TenantAuthorizationStrategy authorizationStrategy
    ) {
        this.properties = properties != null ? properties : new GraphOrmProperties.TenantProperties();
        this.authorizationStrategy = authorizationStrategy;
        this.headerName = this.properties.getHeaderName() != null && !this.properties.getHeaderName().isBlank()
                ? this.properties.getHeaderName()
                : "X-Tenant-ID";
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isHeaderEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawHeader = request.getHeader(headerName);
        Authentication auth = getAuthentication();
        Optional<String> securityTenant = TenantHeaderAuthorizer.resolveSecurityTenant(auth, properties);
        Optional<String> authorizedTenant = TenantHeaderAuthorizer.authorize(
                rawHeader,
                securityTenant,
                auth,
                properties,
                authorizationStrategy
        );

        if (authorizedTenant.isPresent()) {
            try (TenantContext.TenantScope scope = TenantContext.openHeaderScope(authorizedTenant.get())) {
                filterChain.doFilter(request, response);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private Authentication getAuthentication() {
        try {
            SecurityContext context = SecurityContextHolder.getContext();
            return context != null ? context.getAuthentication() : null;
        } catch (NoClassDefFoundError | Exception ignored) {
            return null;
        }
    }

    public String getHeaderName() {
        return headerName;
    }

    public GraphOrmProperties.TenantProperties getProperties() {
        return properties;
    }

    public TenantAuthorizationStrategy getAuthorizationStrategy() {
        return authorizationStrategy;
    }
}
