package com.graphorm.spring.boot.aot;

import com.graphorm.core.exception.*;
import com.graphorm.core.metadata.*;
import com.graphorm.jdbc.dialect.PostgreSqlErrorTranslator;
import com.graphorm.jdbc.exception.QueryExecutionException;
import com.graphorm.schema.GraphOrmLexer;
import com.graphorm.schema.GraphOrmParser;
import com.graphorm.schema.ast.*;
import com.graphorm.schema.parser.DiagnosticErrorListener;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.ValidationResult;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import com.graphorm.spring.boot.graphql.GraphOrmExceptionHandler;
import com.graphorm.spring.boot.metrics.MicrometerDataLoaderMetricsListener;
import com.graphorm.spring.boot.metrics.MicrometerStatementInterceptor;
import com.graphorm.spring.boot.security.SpringSecurityFieldAuthorizer;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.List;
import java.util.UUID;

/**
 * Spring AOT {@link RuntimeHintsRegistrar} providing GraalVM Native-Image reachability hints
 * for GraphORM.
 * <p>
 * Registers reflection, resources, and serialization hints for:
 * <ul>
 *     <li>ANTLR4 lexer, parser, and parse tree contexts</li>
 *     <li>Immutable AST nodes and Diagnostic error listeners</li>
 *     <li>Core metadata records and in-memory ModelRegistry</li>
 *     <li>DynamicRowMapper reflection targets (constructors, record components, scalar conversions)</li>
 *     <li>Portable DataAccessException hierarchy and PostgreSqlErrorTranslator</li>
 *     <li>Spring Boot auto-configuration properties, security authorizers, and metrics interceptors</li>
 *     <li>Schema files and GraphQL SDL resources on the classpath</li>
 * </ul>
 */
public class GraphOrmRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    private static final MemberCategory[] FULL_REFLECTION = {
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.DECLARED_FIELDS
    };

    private static final MemberCategory[] CONSTRUCTORS_AND_METHODS = {
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerReflectionHints(hints);
        registerResourceHints(hints);
        registerSerializationHints(hints);
    }

    private void registerReflectionHints(RuntimeHints hints) {
        var reflection = hints.reflection();

        // 1. Core Metadata Records
        List<Class<?>> metadataClasses = List.of(
                ModelMetadata.class,
                ViewModelMetadata.class,
                CompositeTypeMetadata.class,
                FieldMetadata.class,
                RelationMetadata.class,
                RelationType.class,
                EnumMetadata.class,
                IndexMetadata.class,
                IndexType.class,
                CheckConstraintMetadata.class,
                ExposureMetadata.class,
                ReferentialAction.class,
                EntityMetadata.class,
                EntityType.class,
                ModelRegistry.class,
                ViewJoinMetadata.class
        );
        for (Class<?> cls : metadataClasses) {
            reflection.registerType(cls, FULL_REFLECTION);
        }

        // 2. Schema AST Nodes & Diagnostics
        List<Class<?>> astClasses = List.of(
                SchemaDocument.class,
                DatasourceNode.class,
                GeneratorNode.class,
                ModelNode.class,
                ViewModelNode.class,
                FieldNode.class,
                FieldAttributeNode.class,
                BlockAttributeNode.class,
                EnumNode.class,
                CompositeTypeNode.class,
                ExpressionNode.class,
                StringLiteralNode.class,
                IntLiteralNode.class,
                FloatLiteralNode.class,
                BooleanLiteralNode.class,
                ArrayLiteralNode.class,
                SourcePosition.class,
                Diagnostic.class,
                ValidationResult.class,
                DiagnosticErrorListener.class
        );
        for (Class<?> cls : astClasses) {
            reflection.registerType(cls, FULL_REFLECTION);
        }

        // 3. ANTLR4 Parser & Lexer
        reflection.registerType(GraphOrmLexer.class, FULL_REFLECTION);
        reflection.registerType(GraphOrmParser.class, FULL_REFLECTION);
        for (Class<?> inner : GraphOrmParser.class.getDeclaredClasses()) {
            reflection.registerType(inner, FULL_REFLECTION);
        }

        // ANTLR4 Runtime Classes
        List<String> antlrClasses = List.of(
                "org.antlr.v4.runtime.atn.ATN",
                "org.antlr.v4.runtime.dfa.DFA",
                "org.antlr.v4.runtime.CommonToken",
                "org.antlr.v4.runtime.tree.TerminalNodeImpl",
                "org.antlr.v4.runtime.ParserRuleContext",
                "org.antlr.v4.runtime.RuleContext",
                "org.antlr.v4.runtime.Lexer",
                "org.antlr.v4.runtime.Parser"
        );
        for (String antlrClass : antlrClasses) {
            try {
                Class<?> cls = Class.forName(antlrClass);
                reflection.registerType(cls, FULL_REFLECTION);
            } catch (ClassNotFoundException ignored) {
                // optional runtime class
            }
        }

        // 4. DynamicRowMapper Target Types (common scalar types for record mapping)
        List<Class<?>> scalarTypes = List.of(
                UUID.class,
                BigDecimal.class,
                BigInteger.class,
                LocalDate.class,
                LocalDateTime.class,
                LocalTime.class,
                Instant.class,
                OffsetDateTime.class,
                ZoneOffset.class
        );
        for (Class<?> cls : scalarTypes) {
            reflection.registerType(cls, CONSTRUCTORS_AND_METHODS);
        }

        // 5. Portable Exceptions & Error Translator
        List<Class<?>> exceptionClasses = List.of(
                GraphOrmException.class,
                ReadOnlyViewException.class,
                ConfigurationException.class,
                DataAccessException.class,
                DuplicateKeyException.class,
                ForeignKeyViolationException.class,
                CheckConstraintViolationException.class,
                DataIntegrityViolationException.class,
                OptimisticLockException.class,
                QueryExecutionException.class,
                PostgreSqlErrorTranslator.class
        );
        for (Class<?> cls : exceptionClasses) {
            reflection.registerType(cls, FULL_REFLECTION);
        }

        // 6. Spring Boot Auto-Configuration Beans & Properties
        List<Class<?>> starterClasses = List.of(
                GraphOrmProperties.class,
                GraphOrmProperties.GraphqlProperties.class,
                GraphOrmProperties.LoggingProperties.class,
                GraphOrmProperties.SecurityProperties.class,
                GraphOrmProperties.MetricsProperties.class,
                GraphOrmExceptionHandler.class,
                SpringSecurityFieldAuthorizer.class,
                MicrometerStatementInterceptor.class,
                MicrometerDataLoaderMetricsListener.class
        );
        for (Class<?> cls : starterClasses) {
            reflection.registerType(cls, FULL_REFLECTION);
        }
    }

    private void registerResourceHints(RuntimeHints hints) {
        var resources = hints.resources();
        resources.registerPattern("schema.graphorm");
        resources.registerPattern("schema-*.graphorm");
        resources.registerPattern("*.graphorm");
        resources.registerPattern("graphql/schema.graphqls");
        resources.registerPattern("graphql/*.graphqls");
        resources.registerPattern("graphql/**/*.graphqls");
        resources.registerPattern("META-INF/spring/*");
        resources.registerPattern("META-INF/spring/**");
        resources.registerPattern("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        resources.registerPattern("META-INF/spring/aot.factories");
        resources.registerPattern("META-INF/native-image/**");
    }

    private void registerSerializationHints(RuntimeHints hints) {
        var serialization = hints.serialization();
        serialization.registerType(GraphOrmException.class);
        serialization.registerType(ReadOnlyViewException.class);
        serialization.registerType(ConfigurationException.class);
        serialization.registerType(DataAccessException.class);
        serialization.registerType(DuplicateKeyException.class);
        serialization.registerType(ForeignKeyViolationException.class);
        serialization.registerType(CheckConstraintViolationException.class);
        serialization.registerType(DataIntegrityViolationException.class);
        serialization.registerType(OptimisticLockException.class);
        serialization.registerType(QueryExecutionException.class);
    }
}
