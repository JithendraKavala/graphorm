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
    @DisplayName("Programmatic override cannot be replaced or overwritten by an HTTP header in filter or provider")
    void shouldNotOverwriteProgrammaticOverrideWithHttpHeader() throws ServletException, IOException {
        GraphOrmProperties.TenantProperties props = new GraphOrmProperties.TenantProperties();
        props.setHeaderEnabled(true);
        props.setAllowHeaderOverride(true);
        props.setRequireHeaderAuthorization(false);

        TenantHeaderFilter filter = new TenantHeaderFilter(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "malicious-header-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        TenantContext.runAsTenant("trusted-programmatic-tenant", () -> {
            assertThat(TenantContext.getProgrammaticTenantId()).contains("trusted-programmatic-tenant");
            assertThat(TenantContext.getTenantId()).contains("trusted-programmatic-tenant");
            assertThat(provider.getCurrentTenantId()).contains("trusted-programmatic-tenant");

            // Execute filter inside programmatic scope
            try {
                filter.doFilter(request, response, (req, res) -> {
                    // Header must NOT overwrite the active trusted programmatic override!
                    assertThat(TenantContext.getProgrammaticTenantId()).contains("trusted-programmatic-tenant");
                    assertThat(TenantContext.getTenantId()).contains("trusted-programmatic-tenant");
                    assertThat(provider.getCurrentTenantId()).contains("trusted-programmatic-tenant");
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // After filter, programmatic override remains intact
            assertThat(TenantContext.getProgrammaticTenantId()).contains("trusted-programmatic-tenant");
            assertThat(TenantContext.getTenantId()).contains("trusted-programmatic-tenant");
        });

        // After runAsTenant block exits, context is cleaned up
        assertThat(TenantContext.getProgrammaticTenantId()).isEmpty();
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Nested runAsTenant context restoration after filter execution")
    void testNestedRunAsTenantContextRestorationAfterFilter() throws ServletException, IOException {
        TenantHeaderFilter filter = new TenantHeaderFilter("X-Tenant-ID");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "filter-header-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Start with pre-existing thread tenant
        TenantContext.setTenantId("pre-filter-tenant");

        AtomicBoolean chainRan = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> {
            chainRan.set(true);
            assertThat(TenantContext.getTenantId()).contains("filter-header-tenant");

            // Nested programmatic tenant override
            TenantContext.runAsTenant("nested-programmatic-tenant", () -> {
                assertThat(TenantContext.getProgrammaticTenantId()).contains("nested-programmatic-tenant");
                assertThat(TenantContext.getTenantId()).contains("nested-programmatic-tenant");
            });

            // After nested runAsTenant: restored to filter's header tenant
            assertThat(TenantContext.getProgrammaticTenantId()).isEmpty();
            assertThat(TenantContext.getTenantId()).contains("filter-header-tenant");
        });

        assertThat(chainRan).isTrue();
        // After filter completes: restored to pre-filter tenant, NOT cleared unconditionally!
        assertThat(TenantContext.getTenantId()).contains("pre-filter-tenant");
        assertThat(TenantContext.getProgrammaticTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Context restoration when filter chain throws an exception")
    void testContextRestorationWhenFilterChainThrows() {
        TenantHeaderFilter filter = new TenantHeaderFilter("X-Tenant-ID");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "temp-filter-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantContext.setTenantId("original-tenant");

        try {
            filter.doFilter(request, response, (req, res) -> {
                assertThat(TenantContext.getTenantId()).contains("temp-filter-tenant");
                throw new RuntimeException("Simulated filter chain explosion");
            });
        } catch (Exception ignored) {
        }

        // Original tenant must be restored even on exception
        assertThat(TenantContext.getTenantId()).contains("original-tenant");
        assertThat(TenantContext.getProgrammaticTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Authenticated user without tenant claim: rejected without authorization strategy")
    void shouldRejectHeaderWhenUserHasNoTenantClaimAndNoStrategy() throws ServletException, IOException {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(true);

        TenantHeaderFilter filter = new TenantHeaderFilter(properties, null);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("sub", "user-without-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "untrusted-candidate-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Provider rejects header
        assertThat(provider.getCurrentTenantId()).isEmpty();

        // Filter rejects header and does not expose it to raw ThreadLocal
        AtomicBoolean chainRan = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> {
            chainRan.set(true);
            assertThat(TenantContext.getTenantId()).isEmpty();
        });
        assertThat(chainRan).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Authenticated user without tenant claim: accepted when TenantAuthorizationStrategy approves")
    void shouldAcceptHeaderWhenUserHasNoTenantClaimAndStrategyApproves() throws ServletException, IOException {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(true);

        TenantAuthorizationStrategy strategy = (tenantId, auth) ->
                "allowed-tenant".equals(tenantId) && auth != null && auth.isAuthenticated();

        SpringTenantContextProvider secureProvider = new SpringTenantContextProvider(properties, strategy);
        TenantHeaderFilter filter = new TenantHeaderFilter(properties, strategy);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("sub", "user-without-tenant"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 1. Authorized header
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("X-Tenant-ID", "allowed-tenant");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request1));

        assertThat(secureProvider.getCurrentTenantId()).contains("allowed-tenant");

        AtomicBoolean chainRan1 = new AtomicBoolean(false);
        filter.doFilter(request1, response1, (req, res) -> {
            chainRan1.set(true);
            assertThat(TenantContext.getTenantId()).contains("allowed-tenant");
        });
        assertThat(chainRan1).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();

        // 2. Unauthorized header
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("X-Tenant-ID", "forbidden-tenant");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request2));

        assertThat(secureProvider.getCurrentTenantId()).isEmpty();

        AtomicBoolean chainRan2 = new AtomicBoolean(false);
        filter.doFilter(request2, response2, (req, res) -> {
            chainRan2.set(true);
            assertThat(TenantContext.getTenantId()).isEmpty();
        });
        assertThat(chainRan2).isTrue();
    }

    @Test
    @DisplayName("Header override authorization for user with an existing tenant claim")
    void testHeaderOverrideAuthorizationForUserWithExistingTenantClaim() throws ServletException, IOException {
        properties.setHeaderEnabled(true);
        properties.setRequireHeaderAuthorization(true);

        TenantAuthorizationStrategy strategy = (tenantId, auth) -> "approved-override-tenant".equals(tenantId);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                Map.of("tenant_id", "security-tenant-A"), null
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Subcase A: allowHeaderOverride = false (default)
        properties.setAllowHeaderOverride(false);
        SpringTenantContextProvider providerA = new SpringTenantContextProvider(properties, strategy);
        TenantHeaderFilter filterA = new TenantHeaderFilter(properties, strategy);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "approved-override-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(providerA.getCurrentTenantId()).contains("security-tenant-A");
        filterA.doFilter(request, response, (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEmpty();
        });

        // Subcase B: allowHeaderOverride = true, but strategy denies the requested override
        properties.setAllowHeaderOverride(true);
        MockHttpServletRequest reqDenied = new MockHttpServletRequest();
        reqDenied.addHeader("X-Tenant-ID", "unapproved-tenant");
        MockHttpServletResponse respDenied = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqDenied));

        assertThat(providerA.getCurrentTenantId()).contains("security-tenant-A");
        filterA.doFilter(reqDenied, respDenied, (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEmpty();
        });

        // Subcase C: allowHeaderOverride = true, and strategy approves the requested override
        SpringTenantContextProvider providerC = new SpringTenantContextProvider(properties, strategy);
        TenantHeaderFilter filterC = new TenantHeaderFilter(properties, strategy);

        MockHttpServletRequest reqApproved = new MockHttpServletRequest();
        reqApproved.addHeader("X-Tenant-ID", "approved-override-tenant");
        MockHttpServletResponse respApproved = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqApproved));

        assertThat(providerC.getCurrentTenantId()).contains("approved-override-tenant");
        AtomicBoolean chainRanC = new AtomicBoolean(false);
        filterC.doFilter(reqApproved, respApproved, (req, res) -> {
            chainRanC.set(true);
            assertThat(TenantContext.getTenantId()).contains("approved-override-tenant");
        });
        assertThat(chainRanC).isTrue();
        assertThat(TenantContext.getTenantId()).isEmpty();
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
