package com.graphorm.spring.boot.graphql;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.EntityNotFoundException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.graphql.security.GraphOrmComplexityExceededException;
import com.graphorm.graphql.security.GraphOrmDepthExceededException;
import graphql.GraphQLError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-15.3: GraphOrmExceptionHandler Tests")
class GraphOrmExceptionHandlerTest {

    private final GraphOrmExceptionHandler handler = new GraphOrmExceptionHandler();

    @Test
    @DisplayName("Resolves AccessDeniedException to FORBIDDEN with ACCESS_DENIED code")
    void testAccessDeniedExceptionResolution() {
        AccessDeniedException ex = new AccessDeniedException("User lacks ADMIN role");
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getMessage()).isEqualTo("Access denied: User lacks ADMIN role");
        assertThat(error.getExtensions()).containsEntry("errorCode", "ACCESS_DENIED");
        assertThat(error.getExtensions()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    @DisplayName("Resolves GraphOrmDepthExceededException to GRAPHQL_DEPTH_LIMIT_EXCEEDED")
    void testDepthExceededExceptionResolution() {
        GraphOrmDepthExceededException ex = new GraphOrmDepthExceededException(12, 8);
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getExtensions()).containsEntry("errorCode", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
        assertThat(error.getExtensions()).containsEntry("code", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Resolves GraphOrmComplexityExceededException to GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED")
    void testComplexityExceededExceptionResolution() {
        GraphOrmComplexityExceededException ex = new GraphOrmComplexityExceededException(250, 200);
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getExtensions()).containsEntry("errorCode", "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED");
        assertThat(error.getExtensions()).containsEntry("code", "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Resolves EntityNotFoundException to NOT_FOUND with RECORD_NOT_FOUND code")
    void testEntityNotFoundExceptionResolution() {
        EntityNotFoundException ex = new EntityNotFoundException("Student with id 99 not found");
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getExtensions()).containsEntry("errorCode", "RECORD_NOT_FOUND");
        assertThat(error.getExtensions()).containsEntry("code", "RECORD_NOT_FOUND");
    }

    @Test
    @DisplayName("Resolves ReadOnlyViewException to READ_ONLY_VIEW code")
    void testReadOnlyViewExceptionResolution() {
        ReadOnlyViewException ex = new ReadOnlyViewException("StudentDashboard", "INSERT");
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getExtensions()).containsEntry("errorCode", "READ_ONLY_VIEW");
        assertThat(error.getExtensions()).containsEntry("code", "READ_ONLY_VIEW");
    }

    @Test
    @DisplayName("Sanitizes internal server exceptions to prevent information disclosure")
    void testInternalExceptionSanitization() {
        RuntimeException ex = new RuntimeException("Fatal SQL crash: SELECT * FROM users WHERE password='super_secret_value'");
        List<GraphQLError> errors = handler.resolveException(ex, null).block();

        assertThat(errors).isNotNull().hasSize(1);
        GraphQLError error = errors.get(0);
        assertThat(error.getMessage()).isEqualTo("An internal database error occurred while processing the request");
        assertThat(error.getMessage()).doesNotContain("password").doesNotContain("super_secret_value");
        assertThat(error.getExtensions()).containsEntry("errorCode", "INTERNAL_ERROR");
        assertThat(error.getExtensions()).containsEntry("code", "INTERNAL_ERROR");
    }
}
