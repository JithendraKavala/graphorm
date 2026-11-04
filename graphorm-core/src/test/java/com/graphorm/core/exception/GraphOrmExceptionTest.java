package com.graphorm.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Base Exception Hierarchy Tests")
class GraphOrmExceptionTest {

    @Nested
    @DisplayName("GraphOrmException Tests")
    class GraphOrmExceptionUnitTests {

        @Test
        @DisplayName("Should construct exception with message only")
        void shouldConstructWithMessageOnly() {
            GraphOrmException exception = new GraphOrmException("Operation failed");

            assertThat(exception).isInstanceOf(RuntimeException.class);
            assertThat(exception.getMessage()).isEqualTo("Operation failed");
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getErrorCode()).isNull();
            assertThat(exception.hasErrorCode()).isFalse();
        }

        @Test
        @DisplayName("Should construct exception with message and cause, preserving cause stack trace")
        void shouldConstructWithMessageAndCause() {
            IllegalArgumentException rootCause = new IllegalArgumentException("Invalid argument");
            GraphOrmException exception = new GraphOrmException("Operation failed", rootCause);

            assertThat(exception.getMessage()).isEqualTo("Operation failed");
            assertThat(exception.getCause()).isSameAs(rootCause);
            assertThat(exception.getCause().getMessage()).isEqualTo("Invalid argument");
            assertThat(exception.getErrorCode()).isNull();
            assertThat(exception.hasErrorCode()).isFalse();
        }

        @Test
        @DisplayName("Should construct exception with error code and message")
        void shouldConstructWithErrorCodeAndMessage() {
            GraphOrmException exception = new GraphOrmException("ERR_SCHEMA_001", "Schema syntax error");

            assertThat(exception.getMessage()).isEqualTo("Schema syntax error");
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getErrorCode()).isEqualTo("ERR_SCHEMA_001");
            assertThat(exception.hasErrorCode()).isTrue();
        }

        @Test
        @DisplayName("Should construct exception with error code, message, and cause")
        void shouldConstructWithErrorCodeMessageAndCause() {
            IllegalStateException rootCause = new IllegalStateException("Underlying error");
            GraphOrmException exception = new GraphOrmException("ERR_CORE_002", "Fatal error occurred", rootCause);

            assertThat(exception.getMessage()).isEqualTo("Fatal error occurred");
            assertThat(exception.getCause()).isSameAs(rootCause);
            assertThat(exception.getErrorCode()).isEqualTo("ERR_CORE_002");
            assertThat(exception.hasErrorCode()).isTrue();
        }

        @Test
        @DisplayName("Should return false for hasErrorCode when errorCode is null or blank")
        void shouldHandleNullOrBlankErrorCode() {
            GraphOrmException withNull = new GraphOrmException(null, "Message");
            GraphOrmException withEmpty = new GraphOrmException("", "Message");
            GraphOrmException withWhitespace = new GraphOrmException("   ", "Message");

            assertThat(withNull.hasErrorCode()).isFalse();
            assertThat(withEmpty.hasErrorCode()).isFalse();
            assertThat(withWhitespace.hasErrorCode()).isFalse();
        }
    }

    @Nested
    @DisplayName("ConfigurationException Tests")
    class ConfigurationExceptionUnitTests {

        @Test
        @DisplayName("Should inherit from GraphOrmException and RuntimeException")
        void shouldInheritFromGraphOrmException() {
            ConfigurationException exception = new ConfigurationException("Bad configuration");

            assertThat(exception).isInstanceOf(GraphOrmException.class);
            assertThat(exception).isInstanceOf(RuntimeException.class);
            assertThat(exception.getMessage()).isEqualTo("Bad configuration");
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getErrorCode()).isNull();
            assertThat(exception.hasErrorCode()).isFalse();
        }

        @Test
        @DisplayName("Should construct ConfigurationException with message and cause")
        void shouldConstructWithMessageAndCause() {
            NullPointerException rootCause = new NullPointerException("Property missing");
            ConfigurationException exception = new ConfigurationException("Failed to load config", rootCause);

            assertThat(exception.getMessage()).isEqualTo("Failed to load config");
            assertThat(exception.getCause()).isSameAs(rootCause);
            assertThat(exception.getErrorCode()).isNull();
            assertThat(exception.hasErrorCode()).isFalse();
        }

        @Test
        @DisplayName("Should construct ConfigurationException with error code and message")
        void shouldConstructWithErrorCodeAndMessage() {
            ConfigurationException exception = new ConfigurationException("CONFIG_INVALID_URL", "Database URL is invalid");

            assertThat(exception.getMessage()).isEqualTo("Database URL is invalid");
            assertThat(exception.getErrorCode()).isEqualTo("CONFIG_INVALID_URL");
            assertThat(exception.hasErrorCode()).isTrue();
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("Should construct ConfigurationException with error code, message, and cause")
        void shouldConstructWithErrorCodeMessageAndCause() {
            NumberFormatException rootCause = new NumberFormatException("Not a number");
            ConfigurationException exception = new ConfigurationException("CONFIG_INVALID_PORT", "Port is invalid", rootCause);

            assertThat(exception.getMessage()).isEqualTo("Port is invalid");
            assertThat(exception.getErrorCode()).isEqualTo("CONFIG_INVALID_PORT");
            assertThat(exception.hasErrorCode()).isTrue();
            assertThat(exception.getCause()).isSameAs(rootCause);
        }
    }
}
