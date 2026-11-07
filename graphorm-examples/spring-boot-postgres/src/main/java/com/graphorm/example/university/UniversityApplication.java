package com.graphorm.example.university;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.example.university.generated.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * GraphORM University Reference Application.
 * <p>
 * Demonstrates:
 * <ul>
 *     <li>Prisma-inspired schema-first data modeling</li>
 *     <li>Database-backed physical views (Stage 1) and virtual aggregated views (Stage 2)</li>
 *     <li>Automatic Spring for GraphQL wiring with DataFetchers and DataLoader batching</li>
 *     <li>Field-level authorization via {@code @auth} directive and Spring Security</li>
 *     <li>GraphiQL interactive UI at {@code /graphiql}</li>
 *     <li>Code-generated type-safe Java 21 Client APIs</li>
 * </ul>
 */
@SpringBootApplication
public class UniversityApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityApplication.class, args);
    }

    /**
     * Registers the code-generated typed {@link GraphOrmClient} bean.
     */
    @Bean
    public GraphOrmClient typedGraphOrmClient(
            JdbcExecutor executor,
            ModelRegistry registry,
            SqlDialect dialect
    ) {
        return new GraphOrmClient(executor, registry, dialect);
    }
}
