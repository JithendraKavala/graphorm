package com.graphorm.spring.boot.tenant;

import com.graphorm.core.tenant.TenantContext;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter extracting the tenant identifier from the configured request header (default {@code X-Tenant-ID})
 * and binding it to {@link TenantContext} for the duration of the HTTP request.
 * An authenticated tenant identity takes precedence unless explicit header override is configured.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private final GraphOrmProperties.TenantProperties properties;
    private final String headerName;

    public TenantHeaderFilter(String headerName) {
        this.headerName = headerName != null && !headerName.isBlank() ? headerName : "X-Tenant-ID";
        GraphOrmProperties.TenantProperties props = new GraphOrmProperties.TenantProperties();
        props.setHeaderName(this.headerName);
        props.setHeaderEnabled(true);
        this.properties = props;
    }

    public TenantHeaderFilter(GraphOrmProperties.TenantProperties properties) {
        this.properties = properties != null ? properties : new GraphOrmProperties.TenantProperties();
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

        String tenantId = request.getHeader(headerName);
        if (tenantId != null && !tenantId.isBlank()) {
            // An authenticated tenant must not be overridden by an arbitrary request header unless explicit override is configured
            if (!properties.isAllowHeaderOverride() && hasAuthenticatedSecurityContext()) {
                filterChain.doFilter(request, response);
                return;
            }

            try {
                TenantContext.setTenantId(tenantId.trim());
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean hasAuthenticatedSecurityContext() {
        try {
            SecurityContext context = SecurityContextHolder.getContext();
            if (context == null) {
                return false;
            }
            Authentication auth = context.getAuthentication();
            return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        } catch (NoClassDefFoundError | Exception ignored) {
            return false;
        }
    }

    public String getHeaderName() {
        return headerName;
    }

    public GraphOrmProperties.TenantProperties getProperties() {
        return properties;
    }
}
