package com.graphorm.schema.parser;

import com.graphorm.schema.GraphOrmLexer;
import com.graphorm.schema.GraphOrmParser;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.exception.SchemaValidationException;
import com.graphorm.schema.validation.Diagnostic;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * High-level facade for parsing GraphORM schema DSL into immutable ASTs.
 */
public final class GraphOrmParserFacade {

    private GraphOrmParserFacade() {}

    /**
     * Parses the schema source string into an AST {@link SchemaDocument}.
     * Throws {@link SchemaValidationException} if syntax errors are present.
     *
     * @param source schema source text
     * @return parsed schema document
     */
    public static SchemaDocument parse(String source) {
        return parse(source, "schema.graphorm");
    }

    /**
     * Parses the schema source string with a given source file name.
     *
     * @param source     schema source text
     * @param sourceFile source file name for diagnostics
     * @return parsed schema document
     */
    public static SchemaDocument parse(String source, String sourceFile) {
        ParseResult result = parseWithDiagnostics(source, sourceFile);
        if (result.hasErrors()) {
            throw new SchemaValidationException(result.diagnostics(), source);
        }
        return result.document();
    }

    /**
     * Parses schema from an input stream.
     *
     * @param in input stream
     * @return parsed schema document
     * @throws IOException if reading the stream fails
     */
    public static SchemaDocument parse(InputStream in) throws IOException {
        Objects.requireNonNull(in, "InputStream must not be null");
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return parse(content, "schema.graphorm");
    }

    /**
     * Parses schema from a file path.
     *
     * @param path file path
     * @return parsed schema document
     * @throws IOException if reading the file fails
     */
    public static SchemaDocument parse(Path path) throws IOException {
        Objects.requireNonNull(path, "Path must not be null");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return parse(content, path.getFileName().toString());
    }

    /**
     * Parses the schema without throwing immediately on syntax errors, returning a result object.
     *
     * @param source     schema source code
     * @param sourceFile source identifier
     * @return parse result with document and diagnostic list
     */
    public static ParseResult parseWithDiagnostics(String source, String sourceFile) {
        if (source == null) {
            source = "";
        }
        if (sourceFile == null || sourceFile.isBlank()) {
            sourceFile = "schema.graphorm";
        }

        CharStream stream = CharStreams.fromString(source, sourceFile);
        GraphOrmLexer lexer = new GraphOrmLexer(stream);
        DiagnosticErrorListener listener = new DiagnosticErrorListener(sourceFile);

        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        GraphOrmParser parser = new GraphOrmParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        GraphOrmParser.SchemaDocumentContext docCtx = parser.schemaDocument();
        GraphOrmAstVisitor visitor = new GraphOrmAstVisitor();
        SchemaDocument document = (SchemaDocument) visitor.visit(docCtx);

        return new ParseResult(document, listener.getDiagnostics(), source);
    }

    /**
     * Container holding parsed AST and syntax diagnostics.
     */
    public record ParseResult(
            SchemaDocument document,
            List<Diagnostic> diagnostics,
            String sourceCode
    ) {
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(Diagnostic::isError);
        }
    }
}
