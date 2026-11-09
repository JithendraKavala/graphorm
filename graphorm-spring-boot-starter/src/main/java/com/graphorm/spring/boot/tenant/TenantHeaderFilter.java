package com.graphorm.spring.boot.tenant;

import com.graphorm.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter extracting the tenant identifier from the configured request header (default {@code X-Tenant-ID})
 * and binding it to {@link TenantContext} for the duration of the HTTP request.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    private final String headerName;

    public TenantHeaderFilter(String headerName) {
        this.headerName = headerName != null && !headerName.isBlank() ? headerName : "X-Tenant-ID";
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String tenantId = request.getHeader(headerName);
        if (tenantId != null && !tenantId.isBlank()) {
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

    public String getHeaderName() {
        return headerName;
    }
}
