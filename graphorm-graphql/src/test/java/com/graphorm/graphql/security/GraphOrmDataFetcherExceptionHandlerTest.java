package com.graphorm.graphql.security;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.EntityNotFoundException;
import com.graphorm.core.exception.ReadOnlyViewException;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphOrmDataFetcherExceptionHandler Unit Tests")
class GraphOrmDataFetcherExceptionHandlerTest {

    private final SourceLocation loc = new SourceLocation(1, 1);
    private final ResultPath path = ResultPath.parse("/students");

    @Test
    @DisplayName("Should sanitize AccessDeniedException to ACCESS_DENIED with status 403")
    void shouldSanitizeAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden field ssn");
        GraphQLError error = GraphOrmDataFetcherExceptionHandler.sanitize(ex, loc, path);

        assertThat(error.getMessage()).contains("Access denied");
        assertThat(error.getExtensions()).containsEntry("errorCode", "ACCESS_DENIED");
        assertThat(error.getExtensions()).containsEntry("status", 403);
    }

    @Test
    @DisplayName("Should sanitize EntityNotFoundException to RECORD_NOT_FOUND with status 404")
    void shouldSanitizeEntityNotFoundException() {
        EntityNotFoundException ex = new EntityNotFoundException("Student with id 99 not found");
        GraphQLError error = GraphOrmDataFetcherExceptionHandler.sanitize(ex, loc, path);

        assertThat(error.getMessage()).contains("Student with id 99 not found");
        assertThat(error.getExtensions()).containsEntry("errorCode", "RECORD_NOT_FOUND");
        assertThat(error.getExtensions()).containsEntry("status", 404);
    }

    @Test
    @DisplayName("Should sanitize ReadOnlyViewException to READ_ONLY_VIEW with status 400")
    void shouldSanitizeReadOnlyViewException() {
        ReadOnlyViewException ex = new ReadOnlyViewException("StudentDashboard", "INSERT");
        GraphQLError error = GraphOrmDataFetcherExceptionHandler.sanitize(ex, loc, path);

        assertThat(error.getMessage()).contains("StudentDashboard");
        assertThat(error.getExtensions()).containsEntry("errorCode", "READ_ONLY_VIEW");
        assertThat(error.getExtensions()).containsEntry("status", 400);
    }

    @Test
    @DisplayName("Should sanitize DepthExceededException to GRAPHQL_DEPTH_LIMIT_EXCEEDED with status 400")
    void shouldSanitizeDepthExceededException() {
        GraphOrmDepthExceededException ex = new GraphOrmDepthExceededException(9, 8);
        GraphQLError error = GraphOrmDataFetcherExceptionHandler.sanitize(ex, loc, path);

        assertThat(error.getMessage()).contains("Maximum query depth exceeded 9 > 8");
        assertThat(error.getExtensions()).containsEntry("errorCode", "GRAPHQL_DEPTH_LIMIT_EXCEEDED");
        assertThat(error.getExtensions()).containsEntry("status", 400);
    }

    @Test
    @DisplayName("Should mask database SQL exception without leaking table structure or credentials")
    void shouldMaskDatabaseSQLException() {
        SQLException rawSqlEx = new SQLException("relation \"students_secret_table\" does not exist (postgres://admin:secret@localhost:5432/db)");
        GraphQLError error = GraphOrmDataFetcherExceptionHandler.sanitize(rawSqlEx, loc, path);

        // Client must NEVER see raw database internals, table names, or credentials
        assertThat(error.getMessage()).isEqualTo("An internal database error occurred while processing the request");
        assertThat(error.getMessage()).doesNotContain("students_secret_table", "postgres://", "admin:secret");
        assertThat(error.getExtensions()).containsEntry("errorCode", "INTERNAL_ERROR");
        assertThat(error.getExtensions()).containsEntry("status", 500);
    }
}
