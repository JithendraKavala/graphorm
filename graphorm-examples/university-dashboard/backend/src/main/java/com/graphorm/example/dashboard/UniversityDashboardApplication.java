package com.graphorm.example.dashboard;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.example.dashboard.generated.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point for GraphORM University Dashboard showcase backend.
 */
@SpringBootApplication
public class UniversityDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityDashboardApplication.class, args);
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
