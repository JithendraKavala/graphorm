package com.graphorm.spring.boot.tenant;

import org.springframework.security.core.Authentication;

/**
 * Strategy interface for authorizing and validating tenant identifiers supplied by untrusted sources,
 * such as HTTP request headers (e.g. {@code X-Tenant-ID}).
 * <p>
 * <b>Security Requirements:</b>
 * In multi-tenant systems, HTTP headers are client-controlled and cannot be trusted implicitly.
 * Even when header resolution is enabled via configuration, an untrusted client or an authenticated user
 * without a tenant claim must not be permitted to adopt arbitrary tenant identities without verification.
 * An implementation of this strategy must verify that the requesting principal (or client context)
 * is authorized to access the target tenant.
 */
@FunctionalInterface
public interface TenantAuthorizationStrategy {

    /**
     * Determines whether the specified tenant identifier is authorized for the given authentication.
     *
     * @param tenantId       the tenant identifier extracted from the request header (non-null, non-blank)
     * @param authentication the current Spring Security authentication, or {@code null} if unauthenticated
     * @return {@code true} if the tenant access is authorized; {@code false} otherwise
     */
    boolean isTenantAuthorized(String tenantId, Authentication authentication);
}
