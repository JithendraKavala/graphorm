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
        properties.setRequireHeaderAuthorization(true);
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
    @DisplayName("Programmatic tenant switching (TenantContext.runAsTenant) must take precedence over authenticated security context")
    void shouldPreserveProgrammaticTenantSwitchingOverSecurityContext() {
        // Authenticated user with tenant-A
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "security-tenant-A"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Before runAsTenant: security tenant A is returned
        assertThat(provider.getCurrentTenantId()).contains("security-tenant-A");

        // Inside runAsTenant: programmatic override tenant B takes precedence!
        TenantContext.runAsTenant("programmatic-tenant-B", () -> {
            assertThat(provider.getCurrentTenantId()).contains("programmatic-tenant-B");
        });

        // After runAsTenant: restored to security tenant A
        assertThat(provider.getCurrentTenantId()).contains("security-tenant-A");

        // supplyAsTenant also works
        String result = TenantContext.supplyAsTenant("programmatic-tenant-C", () -> {
            assertThat(provider.getCurrentTenantId()).contains("programmatic-tenant-C");
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(provider.getCurrentTenantId()).contains("security-tenant-A");
    }

    @Test
    @DisplayName("Should prioritize authenticated security context over request header by default")
    void shouldPrioritizeSecurityContextOverHeaderByDefault() {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(false); // even if header authorization is relaxed

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "security-tenant-A"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "header-tenant-B");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Authenticated tenant A must not be overridden by untrusted header tenant B
        assertThat(provider.getCurrentTenantId()).contains("security-tenant-A");
    }

    @Test
    @DisplayName("Reject request header when user has no tenant claim and requireHeaderAuthorization is true without strategy")
    void shouldRejectHeaderWhenNoSecurityTenantClaimAndNoAuthorizationStrategy() {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(true);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("sub", "user-without-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "untrusted-candidate-tenant");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Untrusted header must be rejected
        assertThat(provider.getCurrentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Accept request header when user has no tenant claim and TenantAuthorizationStrategy authorizes it")
    void shouldAcceptHeaderWhenAuthorizedByStrategy() {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(true);

        TenantAuthorizationStrategy strategy = (tenantId, auth) ->
                "allowed-tenant".equals(tenantId) && auth != null && auth.isAuthenticated();

        SpringTenantContextProvider secureProvider = new SpringTenantContextProvider(properties, strategy);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("sub", "user-without-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "allowed-tenant");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Authorized candidate tenant accepted
        assertThat(secureProvider.getCurrentTenantId()).contains("allowed-tenant");

        // Unauthorized candidate tenant rejected
        request.removeHeader("X-Tenant-ID");
        request.addHeader("X-Tenant-ID", "forbidden-tenant");
        assertThat(secureProvider.getCurrentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Should allow header when requireHeaderAuthorization is explicitly disabled for testing")
    void shouldAllowHeaderWhenAuthorizationExplicitlyDisabled() {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(false);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("sub", "user-without-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "header-tenant-fallback");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getCurrentTenantId()).contains("header-tenant-fallback");
    }

    @Test
    @DisplayName("Should allow header override when allowHeaderOverride is true and authorized")
    void shouldAllowHeaderOverrideWhenExplicitlyConfiguredAndAuthorized() {
        properties.setHeaderEnabled(true);
        properties.setAllowHeaderOverride(true);
        properties.setRequireHeaderAuthorization(true);

        TenantAuthorizationStrategy strategy = (tenantId, auth) -> "override-tenant-B".equals(tenantId);
        SpringTenantContextProvider secureProvider = new SpringTenantContextProvider(properties, strategy);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "security-tenant-A"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "override-tenant-B");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(secureProvider.getCurrentTenantId()).contains("override-tenant-B");
    }

    @Test
    @DisplayName("Should ignore request header when headerEnabled is false")
    void shouldIgnoreRequestHeaderWhenDisabled() {
        properties.setHeaderEnabled(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "header-tenant-untrusted");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getCurrentTenantId()).isEmpty();
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
    @DisplayName("TenantHeaderFilter should enforce TenantAuthorizationStrategy when configured")
    void testTenantHeaderFilterWithStrategy() throws ServletException, IOException {
        GraphOrmProperties.TenantProperties props = new GraphOrmProperties.TenantProperties();
        props.setHeaderEnabled(true);
        props.setRequireHeaderAuthorization(true);

        TenantAuthorizationStrategy strategy = (tenantId, auth) -> "valid-tenant".equals(tenantId);
        TenantHeaderFilter filter = new TenantHeaderFilter(props, strategy);

        // 1. Authorized tenant header
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("X-Tenant-ID", "valid-tenant");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        AtomicBoolean chainRan1 = new AtomicBoolean(false);
        filter.doFilter(request1, response1, (req, res) -> {
            chainRan1.set(true);
            assertThat(TenantContext.getTenantId()).contains("valid-tenant");
        });
        assertThat(chainRan1).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();

        // 2. Unauthorized tenant header
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("X-Tenant-ID", "evil-tenant");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        AtomicBoolean chainRan2 = new AtomicBoolean(false);
        filter.doFilter(request2, response2, (req, res) -> {
            chainRan2.set(true);
            assertThat(TenantContext.getTenantId()).isEmpty();
        });
        assertThat(chainRan2).isTrue();
    }

    @Test
    @DisplayName("TenantHeaderFilter should reliably clear context on exceptions and thread reuse")
    void testTenantHeaderFilterExceptionCleanupAndReuse() {
        TenantHeaderFilter filter = new TenantHeaderFilter("X-Tenant-ID");
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("X-Tenant-ID", "failing-tenant");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        // 1. Exception thrown inside chain
        try {
            filter.doFilter(request1, response1, (req, res) -> {
                throw new RuntimeException("Simulated filter failure");
            });
        } catch (Exception ignored) {
        }
        assertThat(TenantContext.getTenantId()).isEmpty();

        // 2. Subsequent request on same thread proceeds cleanly
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        AtomicBoolean secondChainRan = new AtomicBoolean(false);
        try {
            filter.doFilter(request2, response2, (req, res) -> {
                secondChainRan.set(true);
                assertThat(TenantContext.getTenantId()).isEmpty();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(secondChainRan).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("TenantHeaderFilter should ignore header when authenticated and allowHeaderOverride is false")
    void testTenantHeaderFilterWithAuthenticatedUser() throws ServletException, IOException {
        GraphOrmProperties.TenantProperties props = new GraphOrmProperties.TenantProperties();
        props.setHeaderEnabled(true);
        props.setAllowHeaderOverride(false);
        TenantHeaderFilter filter = new TenantHeaderFilter(props);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "auth-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "untrusted-header-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain filterChain = (req, res) -> {
            chainExecuted.set(true);
            // Header value should NOT have overwritten TenantContext
            assertThat(TenantContext.getTenantId()).isEmpty();
        };

        filter.doFilter(request, response, filterChain);
        assertThat(chainExecuted).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();
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
