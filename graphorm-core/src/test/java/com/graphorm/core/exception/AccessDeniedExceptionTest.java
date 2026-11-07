package com.graphorm.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessDeniedException Unit Tests")
class AccessDeniedExceptionTest {

    @Test
    @DisplayName("Should create AccessDeniedException with default error code")
    void shouldCreateWithDefaultErrorCode() {
        AccessDeniedException ex = new AccessDeniedException("Access denied to field ssn");
        assertThat(ex.getMessage()).isEqualTo("Access denied to field ssn");
        assertThat(ex.getErrorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(ex.hasErrorCode()).isTrue();
    }

    @Test
    @DisplayName("Should create AccessDeniedException with custom error code and cause")
    void shouldCreateWithCustomCodeAndCause() {
        RuntimeException cause = new RuntimeException("Underlying auth failure");
        AccessDeniedException ex = new AccessDeniedException("FORBIDDEN", "Forbidden operation", cause);
        assertThat(ex.getMessage()).isEqualTo("Forbidden operation");
        assertThat(ex.getErrorCode()).isEqualTo("FORBIDDEN");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
