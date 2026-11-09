package com.graphorm.spring.boot.tenant;

import com.graphorm.core.tenant.TenantContext;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SpringTenantContextProvider and TenantHeaderFilter Unit Tests")
class SpringTenantContextProviderTest {

    private GraphOrmProperties.TenantProperties properties;
    private SpringTenantContextProvider provider;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();

        properties = new GraphOrmProperties.TenantProperties();
        properties.setHeaderName("X-Tenant-ID");
        properties.setJwtClaim("tenant_id");
        provider = new SpringTenantContextProvider(properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Should return empty optional when no tenant context is set")
    void shouldReturnEmptyWhenNoContext() {
        assertThat(provider.getCurrentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Should prioritize TenantContext ThreadLocal above all else")
    void shouldPrioritizeTenantContext() {
        TenantContext.setTenantId("thread-tenant");

        // Set security token as well
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "security-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Set request header as well
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "header-tenant");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getCurrentTenantId()).contains("thread-tenant");
    }

    @Test
    @DisplayName("Should resolve tenant ID from Spring Security principal map")
    void shouldResolveFromSecurityPrincipalMap() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "acme-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(provider.getCurrentTenantId()).contains("acme-tenant");
    }

    @Test
    @DisplayName("Should resolve tenant ID from Spring Security details map")
    void shouldResolveFromSecurityDetailsMap() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");
        auth.setDetails(Map.of("tenant_id", "details-tenant"));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(provider.getCurrentTenantId()).contains("details-tenant");
    }

    @Test
    @DisplayName("Should resolve tenant ID from HTTP request header via RequestContextHolder")
    void shouldResolveFromRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "http-tenant-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getCurrentTenantId()).contains("http-tenant-123");
    }

    @Test
    @DisplayName("TenantHeaderFilter should populate TenantContext for filter chain and clear it afterwards")
    void testTenantHeaderFilter() throws ServletException, IOException {
        TenantHeaderFilter filter = new TenantHeaderFilter("X-Tenant-ID");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "chain-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain = (req, res) -> {
            chainExecuted.set(true);
            assertThat(TenantContext.getTenantId()).contains("chain-tenant");
        };

        filter.doFilter(request, response, filterChain);

        assertThat(chainExecuted).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty(); // Cleaned up
    }

    @Test
    @DisplayName("TenantHeaderFilter should proceed without context when header is missing")
    void testTenantHeaderFilterWithoutHeader() throws ServletException, IOException {
        TenantHeaderFilter filter = new TenantHeaderFilter("X-Tenant-ID");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain = (req, res) -> {
            chainExecuted.set(true);
            assertThat(TenantContext.getTenantId()).isEmpty();
        };

        filter.doFilter(request, response, filterChain);

        assertThat(chainExecuted).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();
    }
}
